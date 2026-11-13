package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidRouteException;
import com.fusaroute.domain.exception.RouteConflictException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.FareChange;
import com.fusaroute.domain.model.FareDraft;
import com.fusaroute.domain.model.RouteDraft;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.out.AdminRouteRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRouteServiceTest {

    /** 12-nov-2026 a las 21:00 en Bogota: en UTC ya seria el 13, y "hoy" debe ser el 12. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T02:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);
    private static final Instant ACTUALIZADA = Instant.parse("2026-11-01T10:00:00Z");

    @Mock
    private AdminRouteRepositoryPort routes;

    private AdminRouteService service() {
        return new AdminRouteService(routes, CLOCK);
    }

    private static AdminRoute ruta(long id, String nombre, RouteStatus estado, List<Fare> tarifas) {
        return new AdminRoute(id, nombre, RouteType.URBANA, estado, null, null, List.of("Centro", "Terminal"),
                tarifas, ACTUALIZADA);
    }

    private static Fare tarifa(String monto, LocalDate desde) {
        return new Fare(null, new BigDecimal(monto), 0, desde);
    }

    private static RouteDraft borrador(String nombre, String monto) {
        return new RouteDraft(nombre, RouteType.URBANA, null, null, List.of("Centro", "Terminal"),
                List.of(new FareDraft(new BigDecimal(monto), null)), null);
    }

    // --- listado y detalle -----------------------------------------------------------------

    @Test
    void el_listado_trae_todos_los_estados_ordenados_por_nombre_con_la_tarifa_vigente() {
        when(routes.findAll()).thenReturn(List.of(
                ruta(1, "Pablo Bello", RouteStatus.ACTIVA, List.of(tarifa("2600", LocalDate.of(2026, 2, 5)))),
                ruta(2, "Ábaco", RouteStatus.INACTIVA, List.of(tarifa("2500", LocalDate.of(2025, 1, 1)),
                        tarifa("2700", LocalDate.of(2026, 3, 1)))),
                ruta(3, "Camino", RouteStatus.SUSPENDIDA, List.of())));

        List<AdminRoute> result = service().listAll();

        assertThat(result).extracting(AdminRoute::name).containsExactly("Ábaco", "Camino", "Pablo Bello");
        assertThat(result.get(0).fares()).hasSize(1);
        assertThat(result.get(0).currentFare()).contains(new BigDecimal("2700"));
        assertThat(result.get(1).currentFare()).isEmpty();
    }

    @Test
    void el_detalle_devuelve_una_ruta_inactiva() {
        when(routes.findById(5L)).thenReturn(Optional.of(ruta(5, "Vieja", RouteStatus.INACTIVA,
                List.of(tarifa("2600", LocalDate.of(2026, 2, 5))))));

        assertThat(service().getById(5L).status()).isEqualTo(RouteStatus.INACTIVA);
    }

    @Test
    void el_detalle_de_una_ruta_inexistente_es_not_found() {
        when(routes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(99L)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void las_tarifas_futuras_no_se_muestran() {
        when(routes.findById(5L)).thenReturn(Optional.of(ruta(5, "Ruta", RouteStatus.ACTIVA,
                List.of(tarifa("2600", LocalDate.of(2026, 2, 5)), tarifa("3000", HOY.plusDays(1))))));

        assertThat(service().getById(5L).currentFare()).contains(new BigDecimal("2600"));
    }

    // --- crear -----------------------------------------------------------------------------

    @Test
    void crea_con_las_tarifas_vigentes_desde_hoy_en_bogota() {
        RouteDraft draft = borrador("Nueva ruta", "2800");
        when(routes.existsByName("Nueva ruta", null)).thenReturn(false);
        when(routes.create(any(), any())).thenReturn(ruta(8, "Nueva ruta", RouteStatus.ACTIVA,
                List.of(tarifa("2800", HOY))));

        AdminRoute creada = service().create(draft);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Fare>> filas = ArgumentCaptor.forClass(List.class);
        verify(routes).create(eq(draft), filas.capture());
        assertThat(filas.getValue()).containsExactly(new Fare(null, new BigDecimal("2800"), 0, HOY));
        assertThat(creada.id()).isEqualTo(8L);
    }

    @Test
    void crear_con_datos_invalidos_lanza_400_con_todos_los_errores_y_no_toca_la_base() {
        RouteDraft malo = new RouteDraft("a", RouteType.URBANA, null, null, List.of(), List.of(), null);

        assertThatThrownBy(() -> service().create(malo))
                .isInstanceOfSatisfying(InvalidRouteException.class,
                        e -> assertThat(e.getViolations()).hasSizeGreaterThanOrEqualTo(3));
        verifyNoMoreInteractions(routes);
    }

    @Test
    void crear_con_un_nombre_repetido_es_conflicto() {
        when(routes.existsByName("Ya existe", null)).thenReturn(true);

        assertThatThrownBy(() -> service().create(borrador("Ya existe", "2600")))
                .isInstanceOf(RouteConflictException.class);
        verify(routes, never()).create(any(), any());
    }

    @Test
    void crear_con_un_letrero_repetido_es_conflicto() {
        RouteDraft d = borrador("Con letrero", "2600");
        RouteDraft conLetrero = new RouteDraft(d.name(), d.type(), null, null, d.neighborhoods(), d.fares(), "CENTRO");
        when(routes.existsBySignFile("CENTRO", null)).thenReturn(true);

        assertThatThrownBy(() -> service().create(conLetrero)).isInstanceOf(RouteConflictException.class);
        verify(routes, never()).create(any(), any());
    }

    // --- modificar -------------------------------------------------------------------------

    @Test
    void modificar_sin_cambiar_la_tarifa_conserva_su_vigencia() {
        AdminRoute existente = ruta(5, "Ruta", RouteStatus.ACTIVA, List.of(tarifa("2600", LocalDate.of(2026, 2, 5))));
        when(routes.findById(5L)).thenReturn(Optional.of(existente));
        when(routes.existsByName("Ruta renombrada", 5L)).thenReturn(false);
        when(routes.update(eq(5L), any(), any())).thenReturn(existente);

        service().update(5L, borrador("Ruta renombrada", "2600.00"));

        ArgumentCaptor<FareChange> cambio = ArgumentCaptor.forClass(FareChange.class);
        verify(routes).update(eq(5L), any(), cambio.capture());
        assertThat(cambio.getValue().changed()).isFalse();
    }

    @Test
    void modificar_la_tarifa_agrega_una_fila_con_vigencia_de_hoy() {
        AdminRoute existente = ruta(5, "Ruta", RouteStatus.ACTIVA, List.of(tarifa("2600", LocalDate.of(2026, 2, 5))));
        when(routes.findById(5L)).thenReturn(Optional.of(existente));
        when(routes.update(eq(5L), any(), any())).thenReturn(existente);

        service().update(5L, borrador("Ruta", "2900"));

        ArgumentCaptor<FareChange> cambio = ArgumentCaptor.forClass(FareChange.class);
        verify(routes).update(eq(5L), any(), cambio.capture());
        assertThat(cambio.getValue().changed()).isTrue();
        assertThat(cambio.getValue().toWrite()).containsExactly(new Fare(null, new BigDecimal("2900"), 0, HOY));
    }

    @Test
    void modificar_una_ruta_inexistente_es_not_found() {
        when(routes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(99L, borrador("Ruta", "2600")))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void modificar_con_el_nombre_de_otra_ruta_es_conflicto() {
        when(routes.findById(5L)).thenReturn(Optional.of(ruta(5, "Ruta", RouteStatus.ACTIVA, List.of())));
        when(routes.existsByName("Otra", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service().update(5L, borrador("Otra", "2600")))
                .isInstanceOf(RouteConflictException.class);
        verify(routes, never()).update(anyLong(), any(), any());
    }

    @Test
    void modificar_con_datos_invalidos_es_400() {
        when(routes.findById(5L)).thenReturn(Optional.of(ruta(5, "Ruta", RouteStatus.ACTIVA, List.of())));

        assertThatThrownBy(() -> service().update(5L, borrador("  ", "2600")))
                .isInstanceOf(InvalidRouteException.class);
    }

    // --- eliminar y restaurar --------------------------------------------------------------

    @Test
    void eliminar_pasa_la_ruta_a_inactiva_sin_borrar_filas() {
        when(routes.findById(5L)).thenReturn(Optional.of(ruta(5, "Ruta", RouteStatus.ACTIVA, List.of())));

        service().deactivate(5L);

        verify(routes).setStatus(5L, RouteStatus.INACTIVA);
    }

    @Test
    void eliminar_una_ruta_ya_inactiva_no_hace_nada() {
        when(routes.findById(5L)).thenReturn(Optional.of(ruta(5, "Ruta", RouteStatus.INACTIVA, List.of())));

        service().deactivate(5L);

        verify(routes, never()).setStatus(anyLong(), any());
    }

    @Test
    void eliminar_una_ruta_inexistente_es_not_found() {
        when(routes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(99L)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void restaurar_devuelve_la_ruta_a_activa() {
        when(routes.findById(5L)).thenReturn(
                Optional.of(ruta(5, "Ruta", RouteStatus.INACTIVA, List.of())),
                Optional.of(ruta(5, "Ruta", RouteStatus.ACTIVA, List.of())));

        AdminRoute restaurada = service().restore(5L);

        verify(routes).setStatus(5L, RouteStatus.ACTIVA);
        assertThat(restaurada.status()).isEqualTo(RouteStatus.ACTIVA);
    }

    @Test
    void restaurar_una_ruta_que_no_estaba_inactiva_es_conflicto() {
        when(routes.findById(5L)).thenReturn(Optional.of(ruta(5, "Ruta", RouteStatus.ACTIVA, List.of())));

        assertThatThrownBy(() -> service().restore(5L)).isInstanceOf(RouteConflictException.class);
        verify(routes, never()).setStatus(anyLong(), any());
    }

    @Test
    void restaurar_una_ruta_inexistente_es_not_found() {
        when(routes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().restore(99L)).isInstanceOf(RouteNotFoundException.class);
    }
}
