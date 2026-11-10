package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.TrafficSector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracion de V21 (lugares) y V22 (circulo de congestion, velocidades) contra un Postgres real:
 * que las migraciones apliquen, que el mapeo JPA valide con {@code ddl-auto=validate} y que la semilla
 * diga lo que el dominio espera. Se salta solo si no hay Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlacePersistenceAdapter.class, TrafficSectorPersistenceAdapter.class, RouteSpeedPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class GeometrySearchPersistenceIT {

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
    private TrafficSectorPersistenceAdapter sectors;

    @Autowired
    private RouteSpeedPersistenceAdapter speeds;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void la_semilla_trae_al_menos_100_lugares_con_coordenada_y_sin_nombres_repetidos_al_normalizar() {
        List<Place> all = places.findAll();

        assertThat(all.size()).isGreaterThanOrEqualTo(100);
        Set<String> normalized = new HashSet<>();
        for (Place place : all) {
            // La misma regla del dominio, vista desde afuera: un nombre o alias ya usado seria ambiguo.
            assertThat(normalized.add(normalize(place.name()))).as("nombre repetido: %s", place.name()).isTrue();
            assertThat(place.coordinate().latitude()).isBetween(4.15, 4.45);
            assertThat(place.coordinate().longitude()).isBetween(-74.55, -74.25);
        }
        for (Place place : all) {
            for (String alias : place.aliases()) {
                assertThat(normalized.add(normalize(alias))).as("alias repetido: %s", alias).isTrue();
            }
        }
    }

    @Test
    void los_alias_viajan_con_el_lugar_y_se_resuelven_por_nombre() {
        Place indio = places.findAll().stream().filter(p -> p.slug().equals("monumento-el-indio")).findFirst().orElseThrow();

        assertThat(indio.aliases()).contains("Indio");
        assertThat(indio.matchesName("indio")).isTrue();
        assertThat(places.findById(indio.id())).contains(indio);
    }

    @Test
    void los_homonimos_ambiguos_no_dejan_alias_que_elija_uno_al_azar() {
        List<Place> calle3 = places.findAll().stream().filter(p -> p.slug().startsWith("calle-3-")).toList();

        assertThat(calle3).hasSize(3);
        assertThat(calle3).allSatisfy(p -> assertThat(p.aliases()).doesNotContain("Calle 3"));
    }

    @Test
    void v22_deja_las_dos_franjas_del_indio_con_circulo_de_450_m_centrado_en_el_lugar() {
        Place indio = places.findAll().stream().filter(p -> p.slug().equals("monumento-el-indio")).findFirst().orElseThrow();

        List<TrafficSector> indioSectors = sectors.findAll().stream()
                .filter(s -> s.name().startsWith("Indio")).toList();

        assertThat(indioSectors).hasSize(2);
        assertThat(indioSectors).allSatisfy(s -> {
            assertThat(s.hasCircle()).isTrue();
            assertThat(s.radiusMeters()).isEqualTo(450);
            assertThat(s.center()).isEqualTo(indio.coordinate());
        });
        assertThat(indioSectors).extracting(s -> s.startTime().toString()).containsExactlyInAnyOrder("12:00", "17:00");
    }

    @Test
    void las_velocidades_se_guardan_y_se_devuelven_las_mas_recientes_por_ruta() {
        Long routeId = ((Number) entityManager.createNativeQuery("SELECT id FROM routes ORDER BY id LIMIT 1")
                .getSingleResult()).longValue();

        for (int i = 1; i <= 7; i++) {
            speeds.record(routeId, 10 + i);
        }
        entityManager.flush();
        entityManager.clear();

        Map<Long, List<Double>> recent = speeds.recentObservations(5);

        assertThat(recent.get(routeId)).hasSize(5);
        // Mas reciente primero: la ultima insertada (17) abre la lista.
        assertThat(recent.get(routeId).get(0)).isEqualTo(17.0);
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT).replace(".", "")
                .replaceAll("\\s+", " ").trim();
    }
}
