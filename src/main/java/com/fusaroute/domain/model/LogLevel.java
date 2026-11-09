package com.fusaroute.domain.model;

/**
 * Niveles que se guardan en {@code app_logs} (SCRUM-186). Solo estos tres: DEBUG y TRACE
 * nunca llegan a la base porque llenarian los 500 MB del plan gratis en dias.
 */
public enum LogLevel {
    INFO,
    WARN,
    ERROR
}
