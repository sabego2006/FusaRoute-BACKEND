package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.AppLog;
import com.fusaroute.domain.model.LogLevel;

import java.time.Instant;
import java.util.List;

/** Lectura de {@code app_logs} para el panel del administrador (SCRUM-186). */
public interface AppLogQueryPort {

    /**
     * Los logs mas recientes primero.
     *
     * @param level nivel exacto a filtrar, o {@code null} para todos.
     * @param since solo filas con {@code ts >= since}, o {@code null} para sin limite.
     * @param limit maximo de filas; el llamador ya lo acoto.
     */
    List<AppLog> findRecent(LogLevel level, Instant since, int limit);
}
