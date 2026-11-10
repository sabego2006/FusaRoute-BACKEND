package com.fusaroute.infrastructure.adapter.out.monitoring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.fusaroute.domain.model.LogLevel;

import java.time.Instant;

/** Una fila de {@code app_logs} lista para insertar: ya sanitizada y recortada. */
record LogEntry(Instant ts, LogLevel level, String logger, String message, String exception, String thread) {

    static final int MAX_MESSAGE = 2_000;
    static final int MAX_EXCEPTION = 4_000;
    private static final int MAX_NAME = 255;

    static LogEntry from(PendingLog log) {
        String exception = log.throwable() == null
                ? null
                : LogSanitizer.sanitizeAndTruncate(ThrowableProxyUtil.asString(log.throwable()), MAX_EXCEPTION);
        String message = LogSanitizer.sanitizeAndTruncate(log.message(), MAX_MESSAGE);
        return new LogEntry(
                log.ts(),
                toLevel(log.level()),
                LogSanitizer.truncate(log.logger() == null ? "desconocido" : log.logger(), MAX_NAME),
                message == null ? "" : message,
                exception,
                LogSanitizer.truncate(log.thread(), MAX_NAME));
    }

    private static LogLevel toLevel(Level level) {
        if (level != null && level.isGreaterOrEqual(Level.ERROR)) {
            return LogLevel.ERROR;
        }
        if (level != null && level.isGreaterOrEqual(Level.WARN)) {
            return LogLevel.WARN;
        }
        return LogLevel.INFO;
    }
}
