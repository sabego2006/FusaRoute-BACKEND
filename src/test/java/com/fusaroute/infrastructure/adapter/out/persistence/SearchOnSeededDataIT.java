package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.application.usecase.SearchRouteService;
import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.SearchPoint;
import com.fusaroute.domain.model.TrafficSector;
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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La busqueda completa (casos de uso + adaptadores reales + Postgres con las migraciones de verdad)
 * sobre los lugares y trazados sembrados: lo que se verificaria a mano contra DEV, pero aislado, sin
 * tocar ninguna base compartida y sin Google (la llave vacia cae al estimado).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlacePersistenceAdapter.class, RoutePersistenceAdapter.class, TrafficSectorPersistenceAdapter.class,
        RouteSpeedPersistenceAdapter.class, SystemClockTestConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class SearchOnSeededDataIT {

    /** 17:10 en Bogota: dentro del aviso de la franja de la tarde del Indio. */
    private static final Clock TARDE = Clock.fixed(Instant.parse("2026-11-09T22:10:00Z"), ZoneId.of("America/Bogota"));
    private static final Clock MANANA = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Bogota"));

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
    private PlacePersistenceAdapter places;

    @Autowired
    private RoutePersistenceAdapter routes;

    @Autowired
    private TrafficSectorPersistenceAdapter sectors;

    @Autowired
    private RouteSpeedPersistenceAdapter speeds;

    private SearchRouteService service(Clock clock) {
        return new SearchRouteService(routes, places,
                (origin, destination, path) -> {
                    throw new com.fusaroute.domain.exception.TravelTimeUnavailableException("sin llave", null);
                },
                sectors, speeds, new SimpleMeterRegistry(), clock);
    }

    private Place place(String slug) {
        return places.findAll().stream().filter(p -> p.slug().equals(slug)).findFirst().orElseThrow();
    }

    @Test
    void indio_a_terminal_por_id_da_ruta_con_abordaje_tiempo_y_aviso_a_las_17_10() {
        Place indio = place("monumento-el-indio");
        Place terminal = place("terminal-de-transporte");

        RouteSearchResult result = service(TARDE).findBestRoute(
                new SearchPoint.OfPlace(indio.id()), new SearchPoint.OfPlace(terminal.id()));

        assertThat(result.route().signFile()).isNotBlank();
        assertThat(result.boarding()).isPresent();
        assertThat(result.dropOff()).isPresent();
        assertThat(result.etaSource()).isEqualTo(EtaSource.ESTIMATED);
        assertThat(result.estimatedTime()).isGreaterThan(Duration.ZERO);
        assertThat(result.longWalk()).isFalse();
        assertThat(result.origin().label()).isEqualTo("Monumento El Indio");
        // El Indio es el centro del circulo y el origen del tramo: lo cruza si o si.
        assertThat(result.congestionSectors()).extracting(TrafficSector::name).containsExactly("Indio (tarde)");
    }

    @Test
    void el_mismo_viaje_por_la_manana_no_trae_aviso() {
        RouteSearchResult result = service(MANANA).findBestRoute(
                new SearchPoint.OfPlace(place("monumento-el-indio").id()),
                new SearchPoint.OfPlace(place("terminal-de-transporte").id()));

        assertThat(result.congestionSectors()).isEmpty();
    }

    @Test
    void un_nombre_legado_resuelve_al_mismo_lugar_que_el_id() {
        RouteSearchResult byName = service(MANANA).findBestRoute(
                new SearchPoint.OfName("Indio"), new SearchPoint.OfName("Terminal"));
        RouteSearchResult byId = service(MANANA).findBestRoute(
                new SearchPoint.OfPlace(place("monumento-el-indio").id()),
                new SearchPoint.OfPlace(place("terminal-de-transporte").id()));

        assertThat(byName.route().id()).isEqualTo(byId.route().id());
    }

    @Test
    void un_punto_a_unos_500_m_de_una_ruta_aborda_con_caminata_de_unos_650_m() {
        // Un vertice del trazado desplazado 0,0045 grados de latitud (~500 m) hacia el norte.
        Route anyRoute = routes.findAll().stream().filter(Route::hasPath).findFirst().orElseThrow();
        Coordinate onPath = anyRoute.path().get(anyRoute.path().size() / 2);
        Coordinate origin = new Coordinate(onPath.latitude() + 0.0045, onPath.longitude());
        Place destination = place("terminal-de-transporte");

        RouteSearchResult result = service(MANANA).findBestRouteOffline(
                new SearchPoint.OfCoordinate(origin), new SearchPoint.OfPlace(destination.id()));

        // La mejor ruta puede pasar aun mas cerca que la elegida para el vertice; nunca mas lejos de 800 m rectos.
        double walk = result.boarding().orElseThrow().walkingDistanceInMeters();
        assertThat(walk).isBetween(1d, 800d * 1.3);
        assertThat(result.origin().label()).startsWith("Cerca de ");
    }

    @Test
    void un_punto_sin_servicio_cercano_da_404_con_la_ruta_mas_cercana() {
        // Al sur de la zona sembrada, lejos de cualquier trazado.
        Coordinate lejos = new Coordinate(4.20, -74.45);

        assertThatThrownBy(() -> service(MANANA).findBestRouteOffline(
                new SearchPoint.OfCoordinate(lejos), new SearchPoint.OfPlace(place("terminal-de-transporte").id())))
                .isInstanceOfSatisfying(NoRouteAvailableException.class, e -> {
                    assertThat(e.nearestToOrigin()).isNotNull();
                    assertThat(e.nearestToOrigin().distanceInMeters()).isGreaterThan(1_500);
                    assertThat(e.nearestToDestination().distanceInMeters()).isLessThan(1_000);
                });
    }

    @Test
    void sin_llave_la_busqueda_online_no_falla_y_queda_estimada() {
        RouteSearchResult result = service(MANANA).findBestRoute(
                new SearchPoint.OfPlace(place("monumento-el-indio").id()),
                new SearchPoint.OfPlace(place("terminal-de-transporte").id()));

        assertThat(result.etaSource()).isEqualTo(EtaSource.ESTIMATED);
        assertThat(List.of(result.estimatedTime())).allSatisfy(t -> assertThat(t.toMinutes()).isPositive());
    }
}
