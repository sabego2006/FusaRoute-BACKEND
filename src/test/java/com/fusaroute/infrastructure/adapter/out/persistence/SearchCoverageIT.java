package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.application.usecase.SearchRouteService;
import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.model.BoardingPoint;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.SearchPoint;
import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.RouteSpeedPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura de la busqueda por geometria (P1) sobre los datos REALES sembrados por Flyway: para todos
 * los pares ordenados A != B de {@code places}, cuenta cuantos devuelven una ruta con caminata normal
 * (<= 800 m en linea recta al trazado, sin {@code longWalk}). Falla si queda por debajo de
 * {@value #MIN_PERCENT} %, y escribe {@code docs/calidad/cobertura-busqueda.md} con el porcentaje y los
 * pares que fallan.
 *
 * Mide la busqueda offline: es el mismo algoritmo sin Google, determinista y sin red. El archivo que
 * escribe no lleva fecha, asi que una corrida sin cambios no deja diff.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlacePersistenceAdapter.class, RoutePersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class SearchCoverageIT {

    private static final double MIN_PERCENT = 75.0;
    private static final Coordinate CENTER = new Coordinate(4.3364, -74.3638);
    private static final double URBAN_RADIUS_METERS = 4_500;
    private static final Path REPORT = Paths.get("docs/calidad/cobertura-busqueda.md");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private PlacePersistenceAdapter placeAdapter;

    @Autowired
    private RoutePersistenceAdapter routeAdapter;

    @Test
    void al_menos_75_por_ciento_de_los_pares_de_lugares_tienen_ruta_con_caminata_normal() throws IOException {
        // Una sola lectura de la base: el algoritmo se repite 18.000 veces y no debe pagar JPA cada vez.
        List<Place> places = placeAdapter.findAll().stream()
                .sorted(java.util.Comparator.comparing(Place::slug)).toList();
        List<Route> routes = routeAdapter.findAll();

        SearchRouteService service = new SearchRouteService(
                new RouteRepositoryPort() {
                    @Override
                    public List<Route> findAll() {
                        return routes;
                    }

                    @Override
                    public Optional<Route> findById(Long id) {
                        return routes.stream().filter(r -> id.equals(r.id())).findFirst();
                    }
                },
                new PlaceRepositoryPort() {
                    @Override
                    public List<Place> findAll() {
                        return places;
                    }

                    @Override
                    public Optional<Place> findById(Long id) {
                        return places.stream().filter(p -> p.id().equals(id)).findFirst();
                    }
                },
                (origin, destination, path) -> {
                    throw new IllegalStateException("la cobertura es offline: no debe llamar a Google");
                },
                () -> List.<TrafficSector>of(),
                new RouteSpeedPort() {
                    @Override
                    public Map<Long, List<Double>> recentObservations(int perRoute) {
                        return Map.of();
                    }

                    @Override
                    public void record(long routeId, double observedKmh) {
                        // Sin efecto: solo se mide.
                    }
                },
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Bogota")));

        int total = 0;
        int ok = 0;
        int urbanTotal = 0;
        int urbanOk = 0;
        List<String> failures = new ArrayList<>();
        for (Place origin : places) {
            List<String> failedFromHere = new ArrayList<>();
            for (Place destination : places) {
                if (origin.equals(destination)) {
                    continue;
                }
                boolean urban = isUrban(origin) && isUrban(destination);
                boolean covered = coveredWithNormalWalk(service, origin, destination);
                total++;
                ok += covered ? 1 : 0;
                if (urban) {
                    urbanTotal++;
                    urbanOk += covered ? 1 : 0;
                }
                if (!covered) {
                    failedFromHere.add(destination.name());
                }
            }
            if (!failedFromHere.isEmpty()) {
                failures.add("- **" + origin.name() + "** → " + String.join(", ", failedFromHere));
            }
        }

        double percent = 100.0 * ok / total;
        double urbanPercent = 100.0 * urbanOk / urbanTotal;
        writeReport(places.size(), routes, total, ok, percent, urbanTotal, urbanOk, urbanPercent, failures);

        assertThat(percent)
                .as("Cobertura de la busqueda: %s %% (%s de %s pares). Detalle en %s", String.format(Locale.ROOT, "%.1f", percent),
                        ok, total, REPORT)
                .isGreaterThanOrEqualTo(MIN_PERCENT);
    }

    private static boolean isUrban(Place place) {
        return place.coordinate().distanceInMetersTo(CENTER) <= URBAN_RADIUS_METERS;
    }

    /** Hay ruta y la caminata es la normal (<= 800 m): ni long-walk ni solo-por-nombre. */
    private static boolean coveredWithNormalWalk(SearchRouteService service, Place origin, Place destination) {
        try {
            RouteSearchResult result = service.findBestRouteOffline(
                    new SearchPoint.OfPlace(origin.id()), new SearchPoint.OfPlace(destination.id()));
            return !result.longWalk()
                    && result.boarding().isPresent()
                    && result.boarding().get().distanceInMeters() <= BoardingPoint.MAX_WALKING_DISTANCE_METERS
                    && result.dropOff().get().distanceInMeters() <= BoardingPoint.MAX_WALKING_DISTANCE_METERS;
        } catch (NoRouteAvailableException e) {
            return false;
        }
    }

    private static void writeReport(int placeCount, List<Route> routes, int total, int ok, double percent,
                                    int urbanTotal, int urbanOk, double urbanPercent, List<String> failures)
            throws IOException {
        long withPath = routes.stream().filter(Route::hasPath).count();
        StringBuilder md = new StringBuilder();
        md.append("# Cobertura de la búsqueda por geometría\n\n");
        md.append("> Generado por `SearchCoverageIT` (Testcontainers + Flyway). No editar a mano: se reescribe en cada corrida.\n\n");
        md.append("Un par origen→destino **cuenta** si la búsqueda devuelve una ruta directa con el abordaje y la bajada ")
                .append("a ≤ 800 m en línea recta del trazado (caminata normal, sin `longWalk`).\n\n");
        md.append("| Universo | Pares | Con ruta | Cobertura |\n|---|---|---|---|\n");
        md.append(String.format(Locale.ROOT, "| Todos los lugares (%d lugares, %d rutas con trazado) | %d | %d | **%.1f %%** |%n",
                placeCount, withPath, total, ok, percent));
        md.append(String.format(Locale.ROOT, "| Urbanos (ambos a ≤ 4,5 km del centro 4.3364, -74.3638) | %d | %d | **%.1f %%** |%n%n",
                urbanTotal, urbanOk, urbanPercent));
        md.append("Umbral de la prueba: ").append((int) MIN_PERCENT).append(" % sobre todos los lugares.\n\n");
        md.append("## Pares que fallan, por origen\n\n");
        md.append("Para cada origen, los destinos que no tienen ruta directa con caminata normal. ")
                .append("Es lo que hay que mirar para decidir qué trazados agregar o qué lugares revisar.\n\n");
        failures.forEach(line -> md.append(line).append("\n"));
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, md.toString(), StandardCharsets.UTF_8);
    }
}
