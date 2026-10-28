package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficSectorTest {

    /** Trancon de la tarde: 17:00 a 19:00, asi que se avisa desde las 16:30. */
    private static TrafficSector sectorDeLaTarde() {
        return new TrafficSector(1L, "Centro - Carrera 1", Set.of("Centro", "La Pampa"),
                LocalTime.of(17, 0), LocalTime.of(19, 0));
    }

    @Test
    void el_aviso_se_enciende_treinta_minutos_antes() {
        assertThat(sectorDeLaTarde().isWarningActiveAt(LocalTime.of(16, 30))).isTrue();
    }

    @Test
    void un_minuto_antes_de_esos_treinta_todavia_no_avisa() {
        assertThat(sectorDeLaTarde().isWarningActiveAt(LocalTime.of(16, 29))).isFalse();
    }

    @Test
    void durante_la_ventana_sigue_avisando() {
        assertThat(sectorDeLaTarde().isWarningActiveAt(LocalTime.of(18, 0))).isTrue();
    }

    @Test
    void al_llegar_la_hora_de_cierre_se_apaga() {
        // La ventana es [inicio, fin): a las 19:00 en punto ya no se avisa.
        assertThat(sectorDeLaTarde().isWarningActiveAt(LocalTime.of(19, 0))).isFalse();
    }

    @Test
    void fuera_de_la_franja_no_avisa() {
        assertThat(sectorDeLaTarde().isWarningActiveAt(LocalTime.of(9, 0))).isFalse();
    }

    @Test
    void una_ventana_de_madrugada_avisa_desde_la_noche_anterior() {
        // 00:10 a 02:00 empieza a avisarse a las 23:40, o sea cruzando la medianoche.
        TrafficSector madrugada = new TrafficSector(2L, "Salida a Bogota", Set.of("Centro"),
                LocalTime.of(0, 10), LocalTime.of(2, 0));

        assertThat(madrugada.warningStartsAt()).isEqualTo(LocalTime.of(23, 40));
        assertThat(madrugada.isWarningActiveAt(LocalTime.of(23, 45))).isTrue();
        assertThat(madrugada.isWarningActiveAt(LocalTime.of(1, 0))).isTrue();
        assertThat(madrugada.isWarningActiveAt(LocalTime.of(23, 30))).isFalse();
        assertThat(madrugada.isWarningActiveAt(LocalTime.of(2, 0))).isFalse();
    }

    @Test
    void afecta_al_recorrido_que_toca_uno_de_sus_barrios() {
        // Basta con rozarlo: el trancon lo sufre quien lo atraviesa, sin importar por
        // cual de sus calles entre.
        assertThat(sectorDeLaTarde().covers(List.of("Camino Real", "Centro", "Terminal"))).isTrue();
    }

    @Test
    void no_afecta_al_recorrido_que_lo_esquiva() {
        assertThat(sectorDeLaTarde().covers(List.of("Camino Real", "Terminal"))).isFalse();
    }

    @Test
    void un_recorrido_vacio_no_atraviesa_nada() {
        assertThat(sectorDeLaTarde().covers(List.of())).isFalse();
    }
}
