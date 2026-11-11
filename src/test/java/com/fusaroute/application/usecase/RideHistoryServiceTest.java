package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidRideException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Ride;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import com.fusaroute.domain.port.in.RecordRideCommand;
import com.fusaroute.domain.port.out.RideRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideHistoryServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-09T22:42:00Z");

    @Mock
    private RideRepositoryPort rides;

    @Mock
    private RouteRepositoryPort routes;

    @Captor
    private ArgumentCaptor<Ride> guardado;

    private final Clock reloj = Clock.fixed(AHORA, ZoneId.of("America/Bogota"));

    private final List<UsageEvent> eventos = new ArrayList<>();

    private com.fusaroute.domain.port.out.UsageEventRecorderPort registro = eventos::add;

    private RideHistoryService service() {
        return new RideHistoryService(rides, routes, reloj, registro);
    }

    private static Route ruta(RouteStatus status) {
        return new Route(14L, "Centro - Galeria - Terminal", "CENTRO_GALERIA_TERMINAL", RouteType.URBANA,
                status, List.of("Centro", "Terminal de Transporte"), List.of(), List.of());
    }

    private static RecordRideCommand comando(Long placeId, Double lat, Double lng) {
        return new RecordRideCommand(1L, 14L, " Terminal de Transporte ", placeId, lat, lng, "Indio");
    }

    @Test
    void guarda_el_viaje_con_la_hora_del_reloj_y_el_letrero_de_la_ruta() {
        when(routes.findById(14L)).thenReturn(Optional.of(ruta(RouteStatus.ACTIVA)));
        when(rides.saveKeepingLatest(any(), anyInt())).thenAnswer(inv -> inv.getArgument(0));

        Ride ride = service().record(comando(31L, 4.3459, -74.3779));

        verify(rides).saveKeepingLatest(guardado.capture(), eq(Ride.MAX_PER_USER));
        Ride r = guardado.getValue();
        assertThat(r.boardedAt()).isEqualTo(AHORA);
        assertThat(r.destinationLabel()).isEqualTo("Terminal de Transporte");
        assertThat(r.destinationPlaceId()).isEqualTo(31L);
        assertThat(r.destination()).isEqualTo(new Coordinate(4.3459, -74.3779));
        assertThat(r.signFile()).isEqualTo("CENTRO_GALERIA_TERMINAL");
        assertThat(r.routeName()).isEqualTo("Centro - Galeria - Terminal");
        assertThat(ride).isSameAs(r);
    }

    @Test
    void el_destino_del_mapa_viaja_sin_place_id() {
        when(routes.findById(14L)).thenReturn(Optional.of(ruta(RouteStatus.ACTIVA)));
        when(rides.saveKeepingLatest(any(), anyInt())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service().record(comando(null, 4.34, -74.37)).destinationPlaceId()).isNull();
    }

    @Test
    void ruta_inexistente_es_404() {
        when(routes.findById(14L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().record(comando(31L, 4.3, -74.3)))
                .isInstanceOf(RouteNotFoundException.class);
        verify(rides, never()).saveKeepingLatest(any(), anyInt());
    }

    @Test
    void ruta_suspendida_es_404() {
        when(routes.findById(14L)).thenReturn(Optional.of(ruta(RouteStatus.SUSPENDIDA)));

        assertThatThrownBy(() -> service().record(comando(31L, 4.3, -74.3)))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void sin_route_id_es_404_sin_ir_a_la_base() {
        RecordRideCommand sinRuta = new RecordRideCommand(1L, null, "Terminal", null, 4.3, -74.3, "Indio");

        assertThatThrownBy(() -> service().record(sinRuta)).isInstanceOf(RouteNotFoundException.class);
        verify(routes, never()).findById(any());
    }

    @Test
    void sin_coordenadas_es_invalido() {
        when(routes.findById(14L)).thenReturn(Optional.of(ruta(RouteStatus.ACTIVA)));

        assertThatThrownBy(() -> service().record(comando(31L, null, -74.3)))
                .isInstanceOf(InvalidRideException.class);
    }

    @Test
    void coordenada_fuera_de_rango_es_invalida_y_no_un_500() {
        when(routes.findById(14L)).thenReturn(Optional.of(ruta(RouteStatus.ACTIVA)));

        assertThatThrownBy(() -> service().record(comando(31L, 91.0, -74.3)))
                .isInstanceOf(InvalidRideException.class);
    }

    @Test
    void el_historial_pide_los_diez_mas_recientes() {
        when(rides.findRecentByUser(1L, Ride.MAX_PER_USER)).thenReturn(List.of());

        assertThat(service().ridesOf(1L)).isEmpty();
        verify(rides).findRecentByUser(1L, 10);
    }

    @Test
    void ya_me_subi_registra_un_boarded_sin_usuario_ni_coordenadas() {
        when(routes.findById(14L)).thenReturn(Optional.of(ruta(RouteStatus.ACTIVA)));
        when(rides.saveKeepingLatest(any(), anyInt())).thenAnswer(inv -> inv.getArgument(0));

        service().record(comando(31L, 4.3459, -74.3779));

        assertThat(eventos).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(UsageEventType.BOARDED);
            assertThat(e.outcome()).isEqualTo(SearchOutcome.FOUND);
            assertThat(e.routeId()).isEqualTo(14L);
            assertThat(e.originLabel()).isEqualTo("Indio");
            assertThat(e.destinationLabel()).isEqualTo("Terminal de Transporte");
            assertThat(e.occurredAt()).isEqualTo(AHORA);
            assertThat(e.etaSource()).isNull();
        });
    }

    @Test
    void un_viaje_rechazado_no_registra_evento() {
        when(routes.findById(14L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().record(comando(31L, 4.3, -74.3)))
                .isInstanceOf(RouteNotFoundException.class);
        assertThat(eventos).isEmpty();
    }

    @Test
    void si_el_registro_falla_el_viaje_queda_guardado_igual() {
        when(routes.findById(14L)).thenReturn(Optional.of(ruta(RouteStatus.ACTIVA)));
        when(rides.saveKeepingLatest(any(), anyInt())).thenAnswer(inv -> inv.getArgument(0));
        registro = evento -> {
            throw new IllegalStateException("cola rota");
        };

        assertThat(service().record(comando(31L, 4.3459, -74.3779))).isNotNull();
    }
}
