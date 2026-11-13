package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Ride;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.Coordinate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las consultas de suspension (RF-12) contra un Postgres real: que la V28 aplique y valide con el mapeo JPA,
 * que el predicado "efectivamente suspendida" sea una ventana semiabierta y que la lectura publica de rutas y
 * el historial de viajes lo apliquen. Los instantes se fijan en milisegundos exactos para probar los bordes.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RouteSuspensionPersistenceAdapter.class, RoutePersistenceAdapter.class, RidePersistenceAdapter.class,
        SystemClockTestConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class RouteSuspensionPersistenceAdapterIT {

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
    private RouteSuspensionPersistenceAdapter suspensions;
    @Autowired
    private RoutePersistenceAdapter routes;
    @Autowired
    private RidePersistenceAdapter rides;
    @Autowired
    private SpringDataRouteSuspensionRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long adminId;
    private Long routeId;
    private String routeName;

    @BeforeEach
    void creaAdministradorYTomaUnaRutaDeLaSemilla() {
        adminId = ((Number) entityManager.createNativeQuery("""
                INSERT INTO users (name, email, password_hash, role, active)
                VALUES ('Admin', 'admin.susp@x.co', '$2a$12$hash', 'ADMIN', TRUE)
                RETURNING id
                """).getSingleResult()).longValue();
        Object[] ruta = (Object[]) entityManager.createNativeQuery("""
                SELECT id, name FROM routes WHERE status = 'ACTIVA' ORDER BY id LIMIT 1
                """).getSingleResult();
        routeId = ((Number) ruta[0]).longValue();
        routeName = (String) ruta[1];
    }

    private RouteSuspension guarda(Instant inicio, Instant fin) {
        return suspensions.save(new RouteSuspension(null, routeId, routeName, inicio, fin, "Obras", null), adminId);
    }

    private static Instant ms(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void guarda_y_devuelve_la_fila_con_id_y_nombre_de_la_ruta() {
        RouteSuspension s = guarda(ms("2026-11-20T13:00:00Z"), ms("2026-11-22T13:00:00Z"));

        assertThat(s.id()).isNotNull();
        assertThat(s.routeName()).isEqualTo(routeName);
        assertThat(s.reason()).isEqualTo("Obras");
        assertThat(s.liftedAt()).isNull();
        assertThat(suspensions.findById(s.id())).contains(s);
    }

    @Test
    void el_predicado_es_una_ventana_semiabierta() {
        Instant inicio = ms("2026-11-20T13:00:00Z");
        Instant fin = ms("2026-11-22T13:00:00Z");
        guarda(inicio, fin);

        assertThat(suspensions.findEffectiveAt(inicio.minusMillis(1))).isEmpty();
        assertThat(suspensions.findEffectiveAt(inicio)).hasSize(1);
        assertThat(suspensions.findEffectiveAt(fin.minusMillis(1))).hasSize(1);
        assertThat(suspensions.findEffectiveAt(fin)).isEmpty();
        assertThat(repository.findEffectiveRouteIdsAt(inicio)).containsExactly(routeId);
        assertThat(repository.findEffectiveRouteIdsAt(fin)).isEmpty();
    }

    @Test
    void levantarla_la_saca_del_predicado_y_conserva_la_fila() {
        RouteSuspension s = guarda(ms("2026-11-20T13:00:00Z"), ms("2026-11-22T13:00:00Z"));
        Instant cuando = ms("2026-11-21T10:00:00Z");

        RouteSuspension levantada = suspensions.markLifted(s.id(), cuando);

        assertThat(levantada.liftedAt()).isEqualTo(cuando);
        assertThat(suspensions.findEffectiveAt(ms("2026-11-21T11:00:00Z"))).isEmpty();
        assertThat(suspensions.findAll()).singleElement().extracting(RouteSuspension::liftedAt).isEqualTo(cuando);
        // Levantarla otra vez no pisa la primera fecha.
        assertThat(suspensions.markLifted(s.id(), ms("2026-11-21T12:00:00Z")).liftedAt()).isEqualTo(cuando);
    }

    @Test
    void las_no_levantadas_de_una_ruta_incluyen_proximas_y_terminadas_pero_no_las_levantadas() {
        guarda(ms("2026-10-01T00:00:00Z"), ms("2026-10-02T00:00:00Z"));
        RouteSuspension levantada = guarda(ms("2026-11-20T13:00:00Z"), ms("2026-11-22T13:00:00Z"));
        suspensions.markLifted(levantada.id(), ms("2026-11-20T14:00:00Z"));
        guarda(ms("2026-12-01T00:00:00Z"), ms("2026-12-02T00:00:00Z"));

        assertThat(suspensions.findNotLiftedByRoute(routeId)).hasSize(2);
    }

    @Test
    void los_efectivos_salen_por_fin_de_ventana() {
        Instant ahora = ms("2026-11-21T00:00:00Z");
        RouteSuspension tarde = guarda(ms("2026-11-20T00:00:00Z"), ms("2026-11-30T00:00:00Z"));
        RouteSuspension pronto = guarda(ms("2026-11-19T00:00:00Z"), ms("2026-11-22T00:00:00Z"));

        // Dos ventanas sobre la misma ruta no se pueden crear por la API (409), pero el predicado no depende de eso.
        assertThat(suspensions.findEffectiveAt(ahora)).extracting(RouteSuspension::id)
                .containsExactly(pronto.id(), tarde.id());
    }

    @Test
    void la_lectura_publica_de_rutas_presenta_como_suspendida_la_ruta_con_ventana_vigente() {
        Instant ahora = Instant.now();
        guarda(ahora.minus(1, ChronoUnit.HOURS), ahora.plus(1, ChronoUnit.HOURS));

        Optional<Route> ruta = routes.findById(routeId);
        assertThat(ruta).get().extracting(Route::status).isEqualTo(RouteStatus.SUSPENDIDA);
        assertThat(routes.findAll()).filteredOn(r -> r.id().equals(routeId)).extracting(Route::isActive)
                .containsExactly(false);
        // routes.status no cambia en la base.
        assertThat(entityManager.createNativeQuery("SELECT status FROM routes WHERE id = :id")
                .setParameter("id", routeId).getSingleResult()).isEqualTo("ACTIVA");
    }

    @Test
    void una_ventana_futura_o_terminada_no_oculta_la_ruta() {
        Instant ahora = Instant.now();
        guarda(ahora.plus(1, ChronoUnit.HOURS), ahora.plus(2, ChronoUnit.HOURS));
        guarda(ahora.minus(2, ChronoUnit.HOURS), ahora.minus(1, ChronoUnit.HOURS));

        assertThat(routes.findById(routeId)).get().extracting(Route::status).isEqualTo(RouteStatus.ACTIVA);
    }

    @Test
    void el_historial_marca_como_no_disponible_el_viaje_de_una_ruta_suspendida() {
        Instant ahora = Instant.now();
        rides.saveKeepingLatest(new Ride(null, adminId, routeId, routeName, null, "Terminal", null,
                new Coordinate(4.34, -74.37), "Indio", ahora), Ride.MAX_PER_USER);
        assertThat(rides.findRecentByUser(adminId, 10)).extracting(Ride::routeAvailable).containsExactly(true);

        RouteSuspension s = guarda(ahora.minus(1, ChronoUnit.HOURS), ahora.plus(1, ChronoUnit.HOURS));
        assertThat(rides.findRecentByUser(adminId, 10)).extracting(Ride::routeAvailable).containsExactly(false);

        suspensions.markLifted(s.id(), ahora);
        assertThat(rides.findRecentByUser(adminId, 10)).extracting(Ride::routeAvailable).containsExactly(true);
    }

    @Test
    void el_check_de_la_base_rechaza_una_ventana_invertida() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                entityManager.createNativeQuery("""
                        INSERT INTO route_suspensions (route_id, starts_at, ends_at)
                        VALUES (:r, now() + interval '2 hours', now() + interval '1 hour')
                        """).setParameter("r", routeId).executeUpdate());
    }

    @Test
    void borrar_la_ruta_borra_sus_suspensiones_en_cascada() {
        // Las FK de otras tablas impiden borrar una ruta de la semilla con datos colgando; la cascada de la
        // V28 se comprueba con una ruta nueva y sin nada mas.
        Long nueva = ((Number) entityManager.createNativeQuery("""
                INSERT INTO routes (name, route_type, status) VALUES ('ZZ cascada', 'URBANA', 'ACTIVA') RETURNING id
                """).getSingleResult()).longValue();
        suspensions.save(new RouteSuspension(null, nueva, "ZZ cascada", ms("2026-11-20T13:00:00Z"),
                ms("2026-11-22T13:00:00Z"), null, null), adminId);
        entityManager.createNativeQuery("DELETE FROM routes WHERE id = :r").setParameter("r", nueva).executeUpdate();

        assertThat(entityManager.createNativeQuery("SELECT count(*) FROM route_suspensions WHERE route_id = :r")
                .setParameter("r", nueva).getSingleResult()).isEqualTo(0L);
    }

    @Test
    void sin_suspensiones_las_consultas_devuelven_vacio() {
        assertThat(suspensions.findAll()).isEmpty();
        assertThat(suspensions.findEffectiveAt(Instant.now().plus(Duration.ofDays(1)))).isEmpty();
        assertThat(repository.findEffectiveRouteIdsAt(Instant.now())).isEmpty();
    }
}
