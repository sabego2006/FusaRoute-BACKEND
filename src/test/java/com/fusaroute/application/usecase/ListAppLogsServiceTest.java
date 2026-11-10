package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidMonitoringQueryException;
import com.fusaroute.domain.model.AppLog;
import com.fusaroute.domain.model.LogLevel;
import com.fusaroute.domain.port.out.AppLogQueryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListAppLogsServiceTest {

    private static final Instant DESDE = Instant.parse("2026-11-08T00:00:00Z");

    private final AppLogQueryPort puerto = mock(AppLogQueryPort.class);
    private final ListAppLogsService servicio = new ListAppLogsService(puerto);

    @Test
    void sin_limit_usa_100() {
        servicio.list(null, null, null);

        verify(puerto).findRecent(null, null, 100);
    }

    @Test
    void pasa_el_nivel_y_la_fecha_tal_cual() {
        AppLog fila = new AppLog(1L, DESDE, LogLevel.ERROR, "x", "m", null, "main", "dev");
        when(puerto.findRecent(LogLevel.ERROR, DESDE, 25)).thenReturn(List.of(fila));

        assertThat(servicio.list(LogLevel.ERROR, DESDE, 25)).containsExactly(fila);
    }

    @Test
    void acepta_los_extremos_1_y_500() {
        servicio.list(null, null, 1);
        servicio.list(null, null, 500);

        verify(puerto).findRecent(null, null, 1);
        verify(puerto).findRecent(null, null, 500);
    }

    @Test
    void rechaza_limit_fuera_de_1_a_500_sin_consultar_la_base() {
        assertThatThrownBy(() -> servicio.list(null, null, 501)).isInstanceOf(InvalidMonitoringQueryException.class);
        assertThatThrownBy(() -> servicio.list(null, null, 0)).isInstanceOf(InvalidMonitoringQueryException.class);
        assertThatThrownBy(() -> servicio.list(null, null, -3)).isInstanceOf(InvalidMonitoringQueryException.class);

        verifyNoInteractions(puerto);
    }
}
