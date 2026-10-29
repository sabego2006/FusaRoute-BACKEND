package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.RouteSign;
import com.fusaroute.domain.model.SignSense;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integracion de {@link RouteSignPersistenceAdapter} contra un Postgres real
 * (SCRUM-176). Es el UNICO test que verifica el contenido de la semilla V11: hasta
 * esta migracion, nada en el registro probaba que los INSERT de una migracion de
 * datos hicieran lo que su comentario dice.
 *
 * Flyway aplica aqui el historial completo (V1..V11), asi que las 9 rutas y los 10
 * letreros vienen de la semilla real, no de datos de prueba fabricados. La ruta se
 * busca por nombre con una consulta nativa porque el adaptador bajo prueba solo
 * expone {@code findByRouteId} — buscar por id fijo (1, 4, 8...) funcionaria por
 * coincidir con el orden de insercion de V2/V6, pero atarse a eso haria el test
 * fragil ante un reordenamiento de la semilla que no cambia el dato que importa.
 *
 * {@code disabledWithoutDocker = true}, igual que {@link UserPersistenceAdapterIT}:
 * se salta solo si no hay Docker, sin romper el build en una maquina sin el daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RouteSignPersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class RouteSignPersistenceAdapterIT {

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
    private RouteSignPersistenceAdapter adapter;

    @PersistenceContext
    private EntityManager entityManager;

    private Long routeIdByName(String name) {
        return ((Number) entityManager.createNativeQuery("SELECT id FROM routes WHERE name = ?1")
                .setParameter(1, name)
                .getSingleResult()).longValue();
    }

    @Test
    void camino_real_la_pampa_tiene_sus_tres_letreros_en_orden_de_sentido() {
        Long routeId = routeIdByName("Camino Real - La Pampa");

        List<RouteSign> signs = adapter.findByRouteId(routeId);

        assertThat(signs).extracting(RouteSign::fieldId).containsExactly("LC-008", "LC-015", "LC-021");
        assertThat(signs).extracting(RouteSign::sense)
                .containsExactly(SignSense.SUBIENDO, SignSense.BAJANDO, SignSense.BAJANDO);
    }

    @Test
    void un_letrero_trae_sus_placas_y_lineas_con_el_texto_sembrado() {
        Long routeId = routeIdByName("Fusagasugá - Arbeláez");

        List<RouteSign> signs = adapter.findByRouteId(routeId);

        assertThat(signs).hasSize(1);
        RouteSign letrero = signs.get(0);
        assertThat(letrero.fieldId()).isEqualTo("LC-025");
        assertThat(letrero.plates()).hasSize(1);
        assertThat(letrero.plates().get(0).lines()).extracting("text").containsExactly("ARBELAEZ");
    }

    @Test
    void llano_largo_tiene_sus_dos_letreros_sembrados() {
        Long routeId = routeIdByName("Llano Largo - La Clarita (\"100\")");

        List<RouteSign> signs = adapter.findByRouteId(routeId);

        assertThat(signs).extracting(RouteSign::fieldId).containsExactly("LC-029", "LC-022");
    }

    @Test
    void una_ruta_sin_evidencia_de_campo_no_tiene_letreros() {
        // 'Fusagasugá - Pasca' es una de las tres rutas que V11 deja sin letrero a
        // proposito: ningun letrero fotografiado la respalda.
        Long routeId = routeIdByName("Fusagasugá - Pasca");

        assertThat(adapter.findByRouteId(routeId)).isEmpty();
    }

    @Test
    void el_total_sembrado_es_diez_letreros_sobre_seis_rutas() {
        Number total = (Number) entityManager.createNativeQuery("SELECT count(*) FROM route_signs")
                .getSingleResult();
        Number rutasConLetrero = (Number) entityManager
                .createNativeQuery("SELECT count(DISTINCT route_id) FROM route_signs")
                .getSingleResult();

        assertThat(total.intValue()).isEqualTo(10);
        assertThat(rutasConLetrero.intValue()).isEqualTo(6);
    }
}
