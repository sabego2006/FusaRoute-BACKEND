package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.AppLog;
import com.fusaroute.domain.model.LogLevel;

import java.time.Instant;
import java.util.List;

/** Consulta de los logs guardados en la base (SCRUM-186). Solo administrador. */
public interface ListAppLogsUseCase {

    /** Limite por defecto cuando el cliente no manda {@code limit}. */
    int DEFAULT_LIMIT = 100;

    /** Tope duro: pedir mas es un error del cliente, no se recorta en silencio. */
    int MAX_LIMIT = 500;

    /**
     * @param level nivel exacto, o {@code null} para todos.
     * @param since desde cuando, o {@code null}.
     * @param limit cuantas filas; {@code null} usa {@link #DEFAULT_LIMIT}.
     * @throws com.fusaroute.domain.exception.InvalidMonitoringQueryException si
     *         {@code limit} esta fuera de 1..{@link #MAX_LIMIT}
     */
    List<AppLog> list(LogLevel level, Instant since, Integer limit);
}
