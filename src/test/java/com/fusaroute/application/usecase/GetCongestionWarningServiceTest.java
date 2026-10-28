package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.port.out.TrafficSectorRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCongestionWarningServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Mock
    private TrafficSectorRepositoryPort trafficSectorRepository;

    /** Reloj fijo: la regla de los 30 minutos se prueba sin esperar a que den las cinco. */
    private static Clock relojA(int hour, int minute) {
        ZonedDateTime moment = ZonedDateTime.of(2026, 10, 28, hour, minute, 0, 0, BOGOTA);
        return Clock.fixed(Instant.from(moment), BOGOTA);
    }

    private GetCongestionWarningService serviceA(int hour, int minute) {
        return new GetCongestionWarningService(trafficSectorRepository, relojA(hour, minute));
    }

    private static TrafficSector centro() {
        return new TrafficSector(1L, "Centro - Carrera 1", Set.of("Centro"),
                LocalTime.of(17, 0), LocalTime.of(19, 0));
    }

    private static TrafficSector salidaNorte() {
        return new TrafficSector(2L, "Salida norte", Set.of("Terminal"),
                LocalTime.of(6, 0), LocalTime.of(8, 0));
    }

    @Test
    void avisa_del_sector_que_el_recorrido_atraviesa_en_su_franja() {
        when(trafficSectorRepository.findAll()).thenReturn(List.of(centro()));

        CongestionWarning warning = serviceA(17, 30).forRoute(List.of("Camino Real", "Centro"));

        assertThat(warning.isEmpty()).isFalse();
        assertThat(warning.sectors()).extracting(TrafficSector::name).containsExactly("Centro - Carrera 1");
    }

    @Test
    void no_avisa_del_sector_que_el_recorrido_esquiva() {
        // El trancon existe y esta activo, pero el usuario no pasa por ahi: avisarselo
        // seria ruido.
        when(trafficSectorRepository.findAll()).thenReturn(List.of(centro()));

        CongestionWarning warning = serviceA(17, 30).forRoute(List.of("Camino Real", "Terminal"));

        assertThat(warning.isEmpty()).isTrue();
    }

    @Test
    void no_avisa_fuera_de_la_franja_aunque_el_recorrido_pase_por_el_sector() {
        when(trafficSectorRepository.findAll()).thenReturn(List.of(centro()));

        CongestionWarning warning = serviceA(10, 0).forRoute(List.of("Centro"));

        assertThat(warning.isEmpty()).isTrue();
    }

    @Test
    void avisa_de_varios_sectores_cuando_el_recorrido_atraviesa_mas_de_uno() {
        TrafficSector centroDeManana = new TrafficSector(3L, "Centro de mañana", Set.of("Centro"),
                LocalTime.of(6, 30), LocalTime.of(8, 30));
        when(trafficSectorRepository.findAll()).thenReturn(List.of(salidaNorte(), centroDeManana));

        CongestionWarning warning = serviceA(7, 0).forRoute(List.of("Centro", "Terminal"));

        assertThat(warning.sectors()).hasSize(2);
        assertThat(warning.earliestStart()).isEqualTo(LocalTime.of(6, 0));
    }

    @Test
    void sin_sectores_registrados_no_hay_aviso() {
        // Es el estado de hoy: la tabla esta vacia porque no hay observacion de campo.
        when(trafficSectorRepository.findAll()).thenReturn(List.of());

        assertThat(serviceA(17, 30).forRoute(List.of("Centro")).isEmpty()).isTrue();
    }

    @Test
    void un_recorrido_sin_barrios_no_consulta_nada() {
        // Sin recorrido no hay nada que cruzar: se corta antes de ir al repositorio.
        CongestionWarning warning = serviceA(17, 30).forRoute(List.of());

        assertThat(warning.isEmpty()).isTrue();
    }
}
