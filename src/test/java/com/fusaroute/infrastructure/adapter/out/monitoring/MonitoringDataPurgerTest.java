package com.fusaroute.infrastructure.adapter.out.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La purga con almacenes simulados: el corte, la suma y el aislamiento entre las dos tablas. */
class MonitoringDataPurgerTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T12:00:00Z");
    private static final Instant CORTE_30_DIAS = Instant.parse("2026-10-09T12:00:00Z");

    private final JdbcAppLogStore logs = mock(JdbcAppLogStore.class);
    private final JdbcUsageEventStore events = mock(JdbcUsageEventStore.class);
    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);

    @Test
    void borra_lo_anterior_a_30_dias_en_las_dos_tablas_y_suma() {
        when(logs.deleteOlderThan(CORTE_30_DIAS)).thenReturn(4);
        when(events.deleteOlderThan(CORTE_30_DIAS)).thenReturn(6);

        int total = new MonitoringDataPurger(logs, events, reloj, 30).purgeNow();

        assertThat(total).isEqualTo(10);
    }

    @Test
    void el_corte_sigue_a_la_retencion_configurada() {
        new MonitoringDataPurger(logs, events, reloj, 7).purgeNow();

        verify(logs).deleteOlderThan(Instant.parse("2026-11-01T12:00:00Z"));
        verify(events).deleteOlderThan(Instant.parse("2026-11-01T12:00:00Z"));
    }

    @Test
    void si_falla_app_logs_igual_se_purga_search_events() {
        when(logs.deleteOlderThan(CORTE_30_DIAS)).thenThrow(new IllegalStateException("base caida"));
        when(events.deleteOlderThan(CORTE_30_DIAS)).thenReturn(2);

        assertThat(new MonitoringDataPurger(logs, events, reloj, 30).purgeNow()).isEqualTo(2);
    }

    @Test
    void si_falla_search_events_igual_cuenta_lo_de_app_logs() {
        when(logs.deleteOlderThan(CORTE_30_DIAS)).thenReturn(5);
        when(events.deleteOlderThan(CORTE_30_DIAS)).thenThrow(new IllegalStateException("base caida"));

        assertThat(new MonitoringDataPurger(logs, events, reloj, 30).purgeNow()).isEqualTo(5);
    }

    @Test
    void la_tarea_programada_ejecuta_la_purga() {
        new MonitoringDataPurger(logs, events, reloj, 30).purgeScheduled();

        verify(logs).deleteOlderThan(CORTE_30_DIAS);
    }

    @Test
    void una_retencion_menor_a_un_dia_es_un_error_de_configuracion() {
        assertThatThrownBy(() -> new MonitoringDataPurger(logs, events, reloj, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
