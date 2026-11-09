package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidMonitoringQueryException;
import com.fusaroute.domain.port.in.ListAppLogsUseCase;

/** Regla compartida por las dos consultas del administrador: 1..500, por defecto 100. */
final class MonitoringLimit {

    private MonitoringLimit() {
    }

    static int resolve(Integer requested) {
        if (requested == null) {
            return ListAppLogsUseCase.DEFAULT_LIMIT;
        }
        if (requested < 1 || requested > ListAppLogsUseCase.MAX_LIMIT) {
            throw new InvalidMonitoringQueryException(
                    "limit debe estar entre 1 y " + ListAppLogsUseCase.MAX_LIMIT);
        }
        return requested;
    }
}
