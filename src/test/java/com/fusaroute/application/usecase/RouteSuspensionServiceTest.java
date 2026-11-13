package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidSuspensionException;
import com.fusaroute.domain.exception.RouteConflictException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.exception.SuspensionNotFoundException;
import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.SuspensionFilter;
import com.fusaroute.domain.model.SuspensionState;
import com.fusaroute.domain.model.SuspensionView;
import com.fusaroute.domain.port.in.CreateRouteSuspensionCommand;
import com.fusaroute.domain.port.out.AdminRouteRepositoryPort;
import com.fusaroute.domain.port.out.RouteSuspensionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Casos de uso de suspension de rutas (RF-12) con un reloj fijo y un almacen en memoria. */
class RouteSuspensionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final Long ADMIN = 1L;

    private final List<RouteSuspension> store = new ArrayList<>();
    private long nextId = 1;
    private AdminRouteRepositoryPort routes;
    private RouteSuspensionService service;

    private final RouteSuspensionRepositoryPort repository = new RouteSuspensionRepositoryPort() {
        @Override
        public RouteSuspension save(RouteSuspension s, Long createdBy) {
            RouteSuspension saved = new RouteSuspension(nextId++, s.routeId(), s.routeName(), s.startsAt(),
                    s.endsAt(), s.reason(), s.liftedAt());
            store.add(saved);
            return saved;
        }

        @Override
        public Optional<RouteSuspension> findById(Long id) {
            return store.stream().filter(s -> id.equals(s.id())).findFirst();
        }

        @Override
        public List<RouteSuspension> findAll() {
            return List.copyOf(store);
        }

        @Override
        public List<RouteSuspension> findNotLiftedByRoute(Long routeId) {
            return store.stream().filter(s -> routeId.equals(s.routeId()) && s.liftedAt() == null).toList();
        }

        @Override
        public RouteSuspension markLifted(Long id, Instant at) {
            RouteSuspension lifted = findById(id).orElseThrow().lifted(at);
            store.replaceAll(s -> id.equals(s.id()) ? lifted : s);
            return lifted;
        }
    };

    @BeforeEach
    void setUp() {
        routes = mock(AdminRouteRepositoryPort.class);
        when(routes.findById(12L)).thenReturn(Optional.of(ruta(12L, "Centro - Terminal", RouteStatus.ACTIVA)));
        when(routes.findById(13L)).thenReturn(Optional.of(ruta(13L, "Gaitan", RouteStatus.ACTIVA)));
        when(routes.findById(99L)).thenReturn(Optional.of(ruta(99L, "Vieja", RouteStatus.INACTIVA)));
        service = new RouteSuspensionService(repository, routes, Clock.fixed(AHORA, ZoneId.of("America/Bogota")));
    }

    private static AdminRoute ruta(Long id, String nombre, RouteStatus estado) {
        return new AdminRoute(id, nombre, RouteType.URBANA, estado, null, null, List.of("Centro"), List.of(), AHORA);
    }

    private static Instant en(Duration d) {
        return AHORA.plus(d);
    }

    private SuspensionView crea(long ruta, Instant inicio, Instant fin) {
        return service.create(new CreateRouteSuspensionCommand(ruta, inicio, fin, "Obras", ADMIN));
    }

    // --- crear -------------------------------------------------------------------------------

    @Test
    void crea_una_suspension_proxima_con_el_nombre_de_la_ruta() {
        SuspensionView creada = crea(12L, en(Duration.ofDays(1)), en(Duration.ofDays(3)));

        assertThat(creada.state()).isEqualTo(SuspensionState.UPCOMING);
        assertThat(creada.suspension().id()).isEqualTo(1L);
        assertThat(creada.suspension().routeName()).isEqualTo("Centro - Terminal");
        assertThat(creada.suspension().reason()).isEqualTo("Obras");
    }

    @Test
    void una_ventana_que_ya_empezo_queda_vigente_al_crearla() {
        assertThat(crea(12L, en(Duration.ofMinutes(-5)), en(Duration.ofHours(2))).state())
                .isEqualTo(SuspensionState.ACTIVE);
    }

    @Test
    void ruta_inexistente_es_404_aun_con_datos_invalidos() {
        when(routes.findById(500L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateRouteSuspensionCommand(500L, null, null, null, ADMIN)))
                .isInstanceOf(RouteNotFoundException.class);
        assertThat(store).isEmpty();
    }

    @Test
    void datos_invalidos_son_400_con_todas_las_violaciones_y_no_guardan_nada() {
        assertThatThrownBy(() -> service.create(new CreateRouteSuspensionCommand(12L,
                en(Duration.ofDays(3)), en(Duration.ofDays(1)), "ver www.x", ADMIN)))
                .isInstanceOfSatisfying(InvalidSuspensionException.class, e ->
                        assertThat(e.getViolations()).extracting(FieldViolation::field)
                                .containsExactly("endsAt", "reason"));
        assertThat(store).isEmpty();
    }

    @Test
    void una_ruta_inactiva_no_se_puede_suspender() {
        assertThatThrownBy(() -> crea(99L, en(Duration.ofDays(1)), en(Duration.ofDays(2))))
                .isInstanceOf(RouteConflictException.class)
                .hasMessageContaining("inactiva");
        assertThat(store).isEmpty();
    }

    @Test
    void una_ventana_que_se_solapa_con_otra_no_levantada_es_conflicto() {
        crea(12L, en(Duration.ofDays(1)), en(Duration.ofDays(5)));

        assertThatThrownBy(() -> crea(12L, en(Duration.ofDays(4)), en(Duration.ofDays(8))))
                .isInstanceOf(RouteConflictException.class)
                .hasMessageContaining("solapa");
        assertThat(store).hasSize(1);
    }

    @Test
    void ventanas_contiguas_de_la_misma_ruta_son_validas() {
        crea(12L, en(Duration.ofDays(1)), en(Duration.ofDays(5)));

        assertThat(crea(12L, en(Duration.ofDays(5)), en(Duration.ofDays(8))).state())
                .isEqualTo(SuspensionState.UPCOMING);
    }

    @Test
    void el_solape_solo_cuenta_dentro_de_la_misma_ruta() {
        crea(12L, en(Duration.ofDays(1)), en(Duration.ofDays(5)));

        assertThat(crea(13L, en(Duration.ofDays(1)), en(Duration.ofDays(5))).suspension().routeId()).isEqualTo(13L);
    }

    @Test
    void una_ventana_levantada_ya_no_estorba_para_crear_otra_encima() {
        SuspensionView primera = crea(12L, en(Duration.ofDays(1)), en(Duration.ofDays(5)));
        service.lift(primera.suspension().id());

        assertThat(crea(12L, en(Duration.ofDays(2)), en(Duration.ofDays(4))).state())
                .isEqualTo(SuspensionState.UPCOMING);
    }

    // --- reactivar ---------------------------------------------------------------------------

    @Test
    void reactivar_a_mano_una_vigente_fija_liftedAt_y_pasa_a_LIFTED() {
        SuspensionView vigente = crea(12L, en(Duration.ofMinutes(-5)), en(Duration.ofHours(2)));

        SuspensionView levantada = service.lift(vigente.suspension().id());

        assertThat(levantada.state()).isEqualTo(SuspensionState.LIFTED);
        assertThat(levantada.suspension().liftedAt()).isEqualTo(AHORA);
        assertThat(store.get(0).liftedAt()).isEqualTo(AHORA);
    }

    @Test
    void se_puede_levantar_una_suspension_que_aun_no_empezo() {
        SuspensionView proxima = crea(12L, en(Duration.ofDays(1)), en(Duration.ofDays(2)));

        assertThat(service.lift(proxima.suspension().id()).state()).isEqualTo(SuspensionState.LIFTED);
    }

    @Test
    void reactivar_una_inexistente_es_404() {
        assertThatThrownBy(() -> service.lift(777L)).isInstanceOf(SuspensionNotFoundException.class);
    }

    @Test
    void reactivar_dos_veces_es_conflicto_y_conserva_la_primera_fecha() {
        SuspensionView vigente = crea(12L, en(Duration.ofMinutes(-5)), en(Duration.ofHours(2)));
        service.lift(vigente.suspension().id());

        assertThatThrownBy(() -> service.lift(vigente.suspension().id()))
                .isInstanceOf(RouteConflictException.class)
                .hasMessageContaining("levantada");
        assertThat(store.get(0).liftedAt()).isEqualTo(AHORA);
    }

    @Test
    void reactivar_una_que_ya_termino_es_conflicto() {
        store.add(new RouteSuspension(5L, 12L, "Centro - Terminal", en(Duration.ofDays(-3)),
                en(Duration.ofDays(-1)), null, null));

        assertThatThrownBy(() -> service.lift(5L))
                .isInstanceOf(RouteConflictException.class)
                .hasMessageContaining("termino");
    }

    @Test
    void justo_en_endsAt_ya_no_se_puede_reactivar() {
        store.add(new RouteSuspension(5L, 12L, "Centro - Terminal", en(Duration.ofDays(-3)), AHORA, null, null));

        assertThatThrownBy(() -> service.lift(5L)).isInstanceOf(RouteConflictException.class);
    }

    // --- listar ------------------------------------------------------------------------------

    /** Una fila con inicio y fin en dias respecto de AHORA. */
    private void fila(long id, long ruta, long inicioDias, long finDias, Instant levantada) {
        store.add(new RouteSuspension(id, ruta, "Ruta " + ruta, en(Duration.ofDays(inicioDias)),
                en(Duration.ofDays(finDias)), null, levantada));
    }

    private void siembraUnaDeCadaEstado() {
        fila(1, 12, -9, -8, null);    // ENDED vieja
        fila(2, 12, -2, -1, null);    // ENDED reciente
        fila(3, 13, -1, 9, null);     // ACTIVE, termina tarde
        fila(4, 12, -1, 2, null);     // ACTIVE, termina pronto
        fila(5, 13, 10, 12, null);    // UPCOMING, empieza tarde
        fila(6, 12, 4, 6, null);      // UPCOMING, empieza pronto
        fila(7, 13, -1, 30, AHORA);   // LIFTED
    }

    private List<Long> ids(SuspensionFilter filtro) {
        return service.list(filtro).stream().map(v -> v.suspension().id()).toList();
    }

    @Test
    void el_listado_sale_vigentes_luego_proximas_luego_pasadas() {
        siembraUnaDeCadaEstado();

        // vigentes: la que termina antes primero; proximas: la que empieza antes primero;
        // pasadas (terminadas y levantadas): la mas reciente primero.
        assertThat(ids(SuspensionFilter.ALL)).containsExactly(4L, 3L, 6L, 5L, 7L, 2L, 1L);
    }

    @Test
    void cada_filtro_deja_solo_su_grupo() {
        siembraUnaDeCadaEstado();

        assertThat(ids(SuspensionFilter.ACTIVE)).containsExactly(4L, 3L);
        assertThat(ids(SuspensionFilter.UPCOMING)).containsExactly(6L, 5L);
        assertThat(ids(SuspensionFilter.PAST)).containsExactlyInAnyOrder(2L, 7L, 1L);
    }

    @Test
    void el_estado_de_cada_fila_se_calcula_con_el_reloj_del_caso_de_uso() {
        siembraUnaDeCadaEstado();

        assertThat(service.list(SuspensionFilter.ALL)).extracting(SuspensionView::state)
                .containsExactly(SuspensionState.ACTIVE, SuspensionState.ACTIVE, SuspensionState.UPCOMING,
                        SuspensionState.UPCOMING, SuspensionState.LIFTED, SuspensionState.ENDED,
                        SuspensionState.ENDED);
    }

    @Test
    void sin_suspensiones_el_listado_es_vacio() {
        assertThat(service.list(SuspensionFilter.ALL)).isEmpty();
    }
}
