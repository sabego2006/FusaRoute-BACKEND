package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.InvalidRideException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideTest {

    private static final Instant AHORA = Instant.parse("2026-11-09T22:42:00Z");
    private static final Coordinate TERMINAL = new Coordinate(4.3459, -74.3779);

    private static Ride ride(Long userId, Long routeId, String destino, Coordinate c, String origen, Instant t) {
        return new Ride(null, userId, routeId, "Ruta", null, destino, null, c, origen, t);
    }

    @Test
    void recorta_los_espacios_de_las_etiquetas() {
        Ride r = ride(1L, 14L, "  Terminal ", TERMINAL, " Indio ", AHORA);
        assertThat(r.destinationLabel()).isEqualTo("Terminal");
        assertThat(r.originLabel()).isEqualTo("Indio");
    }

    @Test
    void el_tope_es_diez() {
        assertThat(Ride.MAX_PER_USER).isEqualTo(10);
    }

    @Test
    void rechaza_campos_obligatorios_vacios() {
        assertThatThrownBy(() -> ride(null, 14L, "T", TERMINAL, "I", AHORA)).isInstanceOf(InvalidRideException.class);
        assertThatThrownBy(() -> ride(1L, null, "T", TERMINAL, "I", AHORA)).isInstanceOf(InvalidRideException.class);
        assertThatThrownBy(() -> ride(1L, 14L, " ", TERMINAL, "I", AHORA)).isInstanceOf(InvalidRideException.class);
        assertThatThrownBy(() -> ride(1L, 14L, "T", null, "I", AHORA)).isInstanceOf(InvalidRideException.class);
        assertThatThrownBy(() -> ride(1L, 14L, "T", TERMINAL, null, AHORA)).isInstanceOf(InvalidRideException.class);
        assertThatThrownBy(() -> ride(1L, 14L, "T", TERMINAL, "I", null)).isInstanceOf(InvalidRideException.class);
    }

    @Test
    void rechaza_etiquetas_de_mas_de_120_caracteres() {
        String largo = "x".repeat(Ride.MAX_LABEL_LENGTH + 1);
        assertThatThrownBy(() -> ride(1L, 14L, largo, TERMINAL, "I", AHORA))
                .isInstanceOf(InvalidRideException.class)
                .hasMessageContaining("120");
        assertThat(ride(1L, 14L, "x".repeat(120), TERMINAL, "I", AHORA).destinationLabel()).hasSize(120);
    }
}
