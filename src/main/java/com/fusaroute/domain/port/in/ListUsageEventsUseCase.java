package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.UsageEvent;

import java.time.Instant;
import java.util.List;

/** Consulta de los eventos de uso guardados en la base (SCRUM-186). Solo administrador. */
public interface ListUsageEventsUseCase {

    /**
     * @param since desde cuando, o {@code null}.
     * @param limit cuantas filas; {@code null} usa {@link ListAppLogsUseCase#DEFAULT_LIMIT}.
     * @throws com.fusaroute.domain.exception.InvalidMonitoringQueryException si
     *         {@code limit} esta fuera de 1..{@link ListAppLogsUseCase#MAX_LIMIT}
     */
    List<UsageEvent> list(Instant since, Integer limit);
}
