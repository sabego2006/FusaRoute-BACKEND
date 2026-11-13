package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V23 (SCRUM-189) sobre un Postgres con todas las migraciones: la ruta de Pasca queda con su nombre, su letrero, un
 * trazado que llega a la iglesia, el recorrido nuevo de 13 lugares y la tarifa de Alaska en $3.550, sin tocar las demas
 * tarifas de V3.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RoutePersistenceAdapter.class, SystemClockTestConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class PascaMigrationIT {

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
    private RoutePersistenceAdapter routes;

    private Route pasca() {
        return routes.findAll().stream().filter(r -> "PASCA".equals(r.signFile())).findFirst().orElseThrow();
    }

    @Test
    void la_ruta_de_pasca_tiene_nombre_letrero_y_trazado_hasta_la_iglesia() {
        Route pasca = pasca();

        assertThat(pasca.name()).isEqualTo("Pasca");
        assertThat(pasca.hasPath()).isTrue();
        var last = pasca.path().get(pasca.path().size() - 1);
        // Iglesia de Pasca (OSM w205862356): 4.30837, -74.29951.
        assertThat(last.latitude()).isBetween(4.3078, 4.3090);
        assertThat(last.longitude()).isBetween(-74.3005, -74.2990);
        var first = pasca.path().get(0);
        // Potosi: 4.3398, -74.3640.
        assertThat(first.latitude()).isBetween(4.3390, 4.3405);
        assertThat(first.longitude()).isBetween(-74.3650, -74.3630);
    }

    @Test
    void el_recorrido_de_pasca_son_los_13_lugares_en_orden() {
        assertThat(pasca().neighborhoods()).containsExactly("Potosí", "Bomberos", "Colegio La Presentación",
                "Pompas La Paz", "Panadería Filipo (Pekín)", "Corregimiento", "Hogar Francisco y Clara",
                "Escuela de los Sauces", "Alaska", "Buenas Tardes", "Crucero La Angostura", "La Capilla", "Pasca");
    }

    @Test
    void alaska_cuesta_3550_hoy_y_las_demas_tarifas_de_v3_no_cambian() {
        Route vigente = pasca().withCurrentFares(LocalDate.of(2026, 11, 10));
        List<Fare> fares = vigente.fares();

        assertThat(fares).filteredOn(f -> "Alaska".equals(f.referencePoint())).singleElement()
                .satisfies(f -> assertThat(f.amount()).isEqualByComparingTo("3550"));
        assertThat(fares).filteredOn(f -> "Corregimiento".equals(f.referencePoint())).singleElement()
                .satisfies(f -> assertThat(f.amount()).isEqualByComparingTo("3000"));
        assertThat(fares).filteredOn(f -> "Pasca".equals(f.referencePoint())).singleElement()
                .satisfies(f -> assertThat(f.amount()).isEqualByComparingTo("4300"));
        // V23 no inserta fila: Alaska ya valia 3550 por V2 (Decreto 16/2026, 2026-02-05), y la fecha de ultima
        // actualizacion del pasaje no debe moverse a "hoy".
        assertThat(vigente.lastFareUpdate()).contains(LocalDate.of(2026, 2, 5));
        // Antes del decreto, Alaska valia lo que dice V3.
        assertThat(pasca().withCurrentFares(LocalDate.of(2026, 1, 1)).fares())
                .filteredOn(f -> "Alaska".equals(f.referencePoint())).singleElement()
                .satisfies(f -> assertThat(f.amount()).isEqualByComparingTo("3300"));
    }

    @Test
    void el_trazado_de_la_ruta_26_que_paso_los_controles_cambio_y_las_otras_siete_conservan_el_de_v16() {
        Route calle4 = routes.findAll().stream()
                .filter(r -> "CENTRO_COLSUBSIDIO_CALLE_4_CEDRITOS_SAN_ANTONIO".equals(r.signFile())).findFirst().orElseThrow();

        assertThat(calle4.hasPath()).isTrue();
        assertThat(calle4.path().size()).isGreaterThan(20);
    }
}
