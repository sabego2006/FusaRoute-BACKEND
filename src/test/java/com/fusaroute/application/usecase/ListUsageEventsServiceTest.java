package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidMonitoringQueryException;
import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import com.fusaroute.domain.port.out.UsageEventQueryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListUsageEventsServiceTest {

    private static final Instant DESDE = Instant.parse("2026-11-08T00:00:00Z");

    private final UsageEventQueryPort puerto = mock(UsageEventQueryPort.class);
    private final ListUsageEventsService servicio = new ListUsageEventsService(puerto);

    @Test
    void sin_limit_usa_100() {
        servicio.list(null, null);

        verify(puerto).findRecent(null, 100);
    }

    @Test
    void devuelve_lo_que_entrega_el_puerto() {
        UsageEvent evento = UsageEvent.of(DESDE, UsageEventType.SEARCH, "Centro", "Terminal", 3L,
                SearchOutcome.FOUND, EtaSource.TRAFFIC, 800, true);
        when(puerto.findRecent(DESDE, 10)).thenReturn(List.of(evento));

        assertThat(servicio.list(DESDE, 10)).containsExactly(evento);
    }

    @Test
    void rechaza_limit_fuera_de_1_a_500_sin_consultar_la_base() {
        assertThatThrownBy(() -> servicio.list(null, 501)).isInstanceOf(InvalidMonitoringQueryException.class);
        assertThatThrownBy(() -> servicio.list(null, 0)).isInstanceOf(InvalidMonitoringQueryException.class);

        verifyNoInteractions(puerto);
    }
}
