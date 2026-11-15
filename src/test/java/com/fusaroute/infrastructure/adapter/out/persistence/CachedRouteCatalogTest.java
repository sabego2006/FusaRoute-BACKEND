package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El catalogo en memoria (SCRUM-192) no puede servir un dato viejo: aqui se prueba que reutiliza la lectura
 * mientras nada cambia y que, en cuanto el administrador escribe o el reloj cruza el borde de una suspension,
 * la SIGUIENTE lectura ya lo refleja. La "base" son los repositorios simulados: {@code nombre}, {@code estado}
 * y {@code filas} son lo que la base tendria en ese momento.
 */
class CachedRouteCatalogTest {

    private static final Instant T0 = Instant.parse("2026-11-19T14:00:00Z");

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Bogota");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private record Fila(Long id, Long routeId, Instant startsAt, Instant endsAt, Instant liftedAt)
            implements RouteSuspensionRow {
        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Long getRouteId() {
            return routeId;
        }

        @Override
        public String getRouteName() {
            return "Oeste - Este";
        }

        @Override
        public Instant getStartsAt() {
            return startsAt;
        }

        @Override
        public Instant getEndsAt() {
            return endsAt;
        }

        @Override
        public String getReason() {
            return "Obras";
        }

        @Override
        public Instant getLiftedAt() {
            return liftedAt;
        }
    }

    private final MutableClock clock = new MutableClock(T0);
    private final List<RouteSuspensionRow> filas = new ArrayList<>();
    private String nombre = "Oeste - Este";
    private RouteStatus estado = RouteStatus.ACTIVA;

    private SpringDataRouteRepository routeRepo;
    private SpringDataRouteSuspensionRepository suspensionRepo;
    private CachedRouteCatalog catalog;

    @BeforeEach
    void setUp() {
        routeRepo = mock(SpringDataRouteRepository.class);
        when(routeRepo.findAll()).thenAnswer(inv -> List.of(ruta()));
        suspensionRepo = mock(SpringDataRouteSuspensionRepository.class);
        when(suspensionRepo.findAllWithRoute()).thenAnswer(inv -> List.copyOf(filas));
        catalog = new CachedRouteCatalog(new RoutePersistenceAdapter(routeRepo, suspensionRepo, clock), clock);
    }

    @AfterEach
    void sinSincronizacionColgada() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private RouteJpaEntity ruta() {
        RouteJpaEntity e = new RouteJpaEntity();
        ReflectionTestUtils.setField(e, "id", 10L);
        ReflectionTestUtils.setField(e, "name", nombre);
        ReflectionTestUtils.setField(e, "type", RouteType.URBANA);
        ReflectionTestUtils.setField(e, "status", estado);
        ReflectionTestUtils.setField(e, "neighborhoods", Map.of(0, "Oeste", 1, "Este"));
        FareJpaEntity fare = new FareJpaEntity();
        ReflectionTestUtils.setField(fare, "amount", new BigDecimal("2600.00"));
        ReflectionTestUtils.setField(fare, "orderIndex", 0);
        ReflectionTestUtils.setField(fare, "validFrom", LocalDate.of(2026, 2, 5));
        ReflectionTestUtils.setField(e, "fares", List.of(fare));
        ReflectionTestUtils.setField(e, "pathGeoJson",
                "{\"type\":\"LineString\",\"coordinates\":[[-74.40,4.34],[-74.36,4.34]]}");
        return e;
    }

    private Route unica() {
        return catalog.findAll().get(0);
    }

    // --- Reutiliza mientras nada cambia ---

    @Test
    void muchas_lecturas_cuestan_una_sola_ida_a_la_base() {
        for (int i = 0; i < 50; i++) {
            catalog.findAll();
            catalog.findById(10L);
        }

        verify(routeRepo, times(1)).findAll();
        verify(suspensionRepo, times(1)).findAllWithRoute();
    }

    @Test
    void sin_cambios_devuelve_la_misma_lista() {
        assertThat(catalog.findAll()).isSameAs(catalog.findAll());
    }

    @Test
    void una_ruta_que_no_existe_es_vacio() {
        assertThat(catalog.findById(99L)).isEmpty();
    }

    // --- Refleja la escritura del administrador en la siguiente lectura ---

    @Test
    void modificar_la_ruta_se_ve_en_la_siguiente_lectura() {
        assertThat(unica().name()).isEqualTo("Oeste - Este");

        nombre = "Oeste - Este por la Variante";
        catalog.invalidate();

        assertThat(unica().name()).isEqualTo("Oeste - Este por la Variante");
        assertThat(catalog.findById(10L)).get().extracting(Route::name).isEqualTo("Oeste - Este por la Variante");
    }

    @Test
    void desactivar_y_reactivar_se_ven_en_la_siguiente_lectura() {
        assertThat(unica().isActive()).isTrue();

        estado = RouteStatus.INACTIVA;
        catalog.invalidate();
        assertThat(unica().isActive()).isFalse();
        assertThat(catalog.findById(10L)).get().extracting(Route::status).isEqualTo(RouteStatus.INACTIVA);

        estado = RouteStatus.ACTIVA;
        catalog.invalidate();
        assertThat(unica().isActive()).isTrue();
    }

    @Test
    void suspender_y_levantar_se_ven_en_la_siguiente_lectura() {
        assertThat(unica().isActive()).isTrue();

        filas.add(new Fila(1L, 10L, T0.minusSeconds(1), T0.plus(Duration.ofDays(2)), null));
        catalog.invalidate();
        assertThat(unica().status()).isEqualTo(RouteStatus.SUSPENDIDA);
        assertThat(catalog.findById(10L)).get().extracting(Route::isActive).isEqualTo(false);

        filas.set(0, new Fila(1L, 10L, T0.minusSeconds(1), T0.plus(Duration.ofDays(2)), T0));
        catalog.invalidate();
        assertThat(unica().isActive()).isTrue();
    }

    @Test
    void sin_invalidar_no_se_vuelve_a_leer_la_base() {
        // El contrato tiene dos caras: si nadie escribe, no hay por que pagar otra lectura.
        unica();
        nombre = "Cambio hecho por fuera de la aplicacion";

        assertThat(unica().name()).isEqualTo("Oeste - Este");
        verify(routeRepo, times(1)).findAll();
    }

    // --- La suspension depende del reloj, no de una relectura ---

    @Test
    void la_ventana_de_suspension_se_abre_y_se_cierra_sola_con_el_reloj() {
        Instant inicio = T0.plus(Duration.ofMinutes(2));
        Instant fin = T0.plus(Duration.ofMinutes(5));
        filas.add(new Fila(1L, 10L, inicio, fin, null));

        clock.set(inicio.minusMillis(1));
        assertThat(unica().isActive()).isTrue();
        clock.set(inicio);
        assertThat(unica().status()).isEqualTo(RouteStatus.SUSPENDIDA);
        clock.set(fin.minusMillis(1));
        assertThat(catalog.findById(10L)).get().extracting(Route::status).isEqualTo(RouteStatus.SUSPENDIDA);
        clock.set(fin);
        assertThat(unica().isActive()).isTrue();

        // Los cuatro cambios de estado salieron de la misma foto: nadie releyo la base.
        verify(routeRepo, times(1)).findAll();
        verify(suspensionRepo, times(1)).findAllWithRoute();
    }

    @Test
    void una_ruta_inactiva_sigue_inactiva_aunque_tenga_una_ventana_vigente() {
        estado = RouteStatus.INACTIVA;
        filas.add(new Fila(1L, 10L, T0.minusSeconds(1), T0.plus(Duration.ofDays(1)), null));

        assertThat(unica().status()).isEqualTo(RouteStatus.INACTIVA);
    }

    @Test
    void la_suspension_de_otra_ruta_no_afecta_a_esta() {
        filas.add(new Fila(1L, 99L, T0.minusSeconds(1), T0.plus(Duration.ofDays(1)), null));

        assertThat(unica().isActive()).isTrue();
    }

    // --- La invalidacion no se puede perder ---

    @Test
    void una_lectura_que_empezo_antes_del_cambio_no_deja_guardado_lo_viejo() {
        // Carrera: el lector ya consulto la base (ve "Oeste - Este") y, antes de que guarde su foto, el
        // administrador confirma el cambio e invalida. Esa foto vieja no puede quedar como la vigente.
        AtomicBoolean primeraLectura = new AtomicBoolean(true);
        when(routeRepo.findAll()).thenAnswer(inv -> {
            List<RouteJpaEntity> leido = List.of(ruta());
            if (primeraLectura.getAndSet(false)) {
                nombre = "Nombre nuevo";
                catalog.invalidate();
            }
            return leido;
        });

        assertThat(unica().name()).isEqualTo("Oeste - Este");
        assertThat(unica().name()).isEqualTo("Nombre nuevo");
        verify(routeRepo, times(2)).findAll();
    }

    @Test
    void dentro_de_una_transaccion_invalida_al_confirmar_y_no_antes() {
        unica();
        TransactionSynchronizationManager.initSynchronization();

        nombre = "Nombre nuevo";
        catalog.invalidateAfterCommit();
        // Todavia sin commit: otra peticion sigue viendo lo confirmado, que es lo correcto.
        assertThat(unica().name()).isEqualTo("Oeste - Este");

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(unica().name()).isEqualTo("Nombre nuevo");
    }

    @Test
    void sin_transaccion_activa_invalida_de_inmediato() {
        unica();

        nombre = "Nombre nuevo";
        catalog.invalidateAfterCommit();

        assertThat(unica().name()).isEqualTo("Nombre nuevo");
    }

    // --- Red de seguridad para cambios hechos por fuera de la aplicacion ---

    @Test
    void un_cambio_hecho_directo_en_la_base_se_ve_al_vencer_el_tiempo_de_vida() {
        unica();
        nombre = "Corregido con un UPDATE a mano";

        clock.set(T0.plusMillis(CachedRouteCatalog.TTL_MILLIS - 1));
        assertThat(unica().name()).isEqualTo("Oeste - Este");
        clock.set(T0.plusMillis(CachedRouteCatalog.TTL_MILLIS));
        assertThat(unica().name()).isEqualTo("Corregido con un UPDATE a mano");
    }
}
