package com.fusaroute.domain.model;

import java.time.Instant;

/**
 * Una fila de {@code app_logs} tal como la lee el panel del administrador (SCRUM-186).
 * Solo se lee desde el dominio: la escritura la hace el appender de Logback, que vive
 * en infraestructura porque depende de Logback.
 *
 * @param id        identificador de la fila.
 * @param ts        cuando ocurrio el evento (no cuando se escribio).
 * @param level     INFO, WARN o ERROR.
 * @param logger    clase o categoria que lo emitio.
 * @param message   mensaje ya sanitizado, de 2000 caracteres como maximo.
 * @param exception pila recortada a 4000 caracteres; nula si el log no traia excepcion.
 * @param thread    hilo que lo emitio.
 * @param profile   perfil de Spring activo (dev, pre, prod).
 */
public record AppLog(
        Long id,
        Instant ts,
        LogLevel level,
        String logger,
        String message,
        String exception,
        String thread,
        String profile) {
}
