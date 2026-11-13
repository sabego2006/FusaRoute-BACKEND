package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Ride;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de {@link RidePersistenceAdapter}, con el repositorio simulado.
 * El tope de 10 contra la base real lo verifica {@link RidePersistenceAdapterIT};
 * esta cubre el mapeo y el orden guardar-luego-recortar sin Docker.
 */
@ExtendWith(MockitoExtension.class)
class RidePersistenceAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-11-09T22:42:00Z");
    private static final Coordinate TERMINAL = new Coordinate(4.3459, -74.3779);

    @Mock
    private SpringDataRideHistoryRepository repository;

    @Captor
    private ArgumentCaptor<RideHistoryJpaEntity> entidad;

    private RidePersistenceAdapter adapter() {
        return new RidePersistenceAdapter(repository);
    }

    private static RideHistoryJpaEntity conId(RideHistoryJpaEntity entity, long id) {
        try {
            var campo = RideHistoryJpaEntity.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo fijar el id de prueba", e);
        }
        return entity;
    }

    @Test
    void guarda_y_luego_recorta_al_tope_devolviendo_el_id_de_la_base() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> conId(inv.getArgument(0), 42L));
        Ride ride = new Ride(null, 7L, 14L, "Centro - Terminal", "CENTRO_GALERIA_TERMINAL",
                "Terminal de Transporte", 31L, TERMINAL, "Indio", AHORA);

        Ride guardado = adapter().saveKeepingLatest(ride, Ride.MAX_PER_USER);

        InOrder orden = inOrder(repository);
        orden.verify(repository).saveAndFlush(entidad.capture());
        orden.verify(repository).deleteOlderBeyond(7L, 10);
        assertThat(guardado.id()).isEqualTo(42L);
        assertThat(guardado.signFile()).isEqualTo("CENTRO_GALERIA_TERMINAL");
        assertThat(guardado.destination()).isEqualTo(TERMINAL);
        assertThat(entidad.getValue().getId()).isEqualTo(42L);
    }

    @Test
    void un_viaje_de_una_ruta_inactiva_se_conserva_marcado_como_no_disponible() {
        RideHistoryRow activa = fila("ACTIVA");
        RideHistoryRow inactiva = fila("INACTIVA");
        when(repository.findRecentByUser(7L, 10)).thenReturn(List.of(activa, inactiva));

        assertThat(adapter().findRecentByUser(7L, 10)).extracting(Ride::routeAvailable)
                .containsExactly(true, false);
    }

    private static RideHistoryRow fila(String estadoRuta) {
        RideHistoryRow fila = mock(RideHistoryRow.class);
        when(fila.getId()).thenReturn(5L);
        when(fila.getUserId()).thenReturn(7L);
        when(fila.getRouteId()).thenReturn(14L);
        when(fila.getRouteName()).thenReturn("Centro - Terminal");
        when(fila.getRouteStatus()).thenReturn(estadoRuta);
        when(fila.getDestinationLabel()).thenReturn("Terminal");
        when(fila.getDestinationLat()).thenReturn(4.34);
        when(fila.getDestinationLng()).thenReturn(-74.37);
        when(fila.getOriginLabel()).thenReturn("Indio");
        when(fila.getBoardedAt()).thenReturn(AHORA);
        return fila;
    }

    @Test
    void lee_las_filas_y_las_traduce_al_dominio() {
        RideHistoryRow fila = mock(RideHistoryRow.class);
        when(fila.getId()).thenReturn(5L);
        when(fila.getUserId()).thenReturn(7L);
        when(fila.getRouteId()).thenReturn(14L);
        when(fila.getRouteName()).thenReturn("Centro - Terminal");
        when(fila.getSignFile()).thenReturn(null);
        when(fila.getDestinationLabel()).thenReturn("Punto en el mapa");
        when(fila.getDestinationPlaceId()).thenReturn(null);
        when(fila.getDestinationLat()).thenReturn(4.34);
        when(fila.getDestinationLng()).thenReturn(-74.37);
        when(fila.getOriginLabel()).thenReturn("Indio");
        when(fila.getBoardedAt()).thenReturn(AHORA);
        when(repository.findRecentByUser(7L, 10)).thenReturn(List.of(fila));

        List<Ride> viajes = adapter().findRecentByUser(7L, 10);

        assertThat(viajes).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(5L);
            assertThat(r.signFile()).isNull();
            assertThat(r.destinationPlaceId()).isNull();
            assertThat(r.destination()).isEqualTo(new Coordinate(4.34, -74.37));
            assertThat(r.boardedAt()).isEqualTo(AHORA);
        });
    }
}
