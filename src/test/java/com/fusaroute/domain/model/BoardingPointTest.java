package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardingPointTest {

    private static final Coordinate CUALQUIERA = new Coordinate(4.3433, -74.3661);

    @Test
    void a_cinco_kilometros_por_hora_cinco_kilometros_toman_una_hora() {
        BoardingPoint lejano = new BoardingPoint(CUALQUIERA, 5_000, 0);

        assertThat(lejano.walkingTime()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void doscientos_cuarenta_metros_son_unos_tres_minutos_de_caminata() {
        BoardingPoint abordaje = new BoardingPoint(CUALQUIERA, 240, 0);

        assertThat(abordaje.walkingTime().toSeconds()).isEqualTo(173);
    }

    @Test
    void ochocientos_metros_justos_siguen_siendo_caminables() {
        // El criterio dice "a mas de 800 m se descarta", asi que el limite entra.
        BoardingPoint enElLimite = new BoardingPoint(CUALQUIERA, BoardingPoint.MAX_WALKING_DISTANCE_METERS, 0);

        assertThat(enElLimite.isWithinWalkingDistance()).isTrue();
    }

    @Test
    void pasados_los_ochocientos_metros_deja_de_ser_caminable() {
        BoardingPoint lejano = new BoardingPoint(CUALQUIERA, 800.01, 0);

        assertThat(lejano.isWithinWalkingDistance()).isFalse();
    }

    @Test
    void una_distancia_negativa_es_un_error_de_programacion() {
        assertThatThrownBy(() -> new BoardingPoint(CUALQUIERA, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser negativa");
    }
}
