package com.fusaroute.infrastructure.adapter.out.monitoring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;

import java.time.Instant;

/**
 * Lo minimo que el hilo que loguea copia de un evento. Se hace barato a proposito: el
 * saneamiento con expresiones regulares y el volcado de la pila corren despues, en el
 * hilo escritor, para no encarecer cada {@code log.warn} de una peticion.
 */
record PendingLog(Instant ts, Level level, String logger, String thread, String message,
                  IThrowableProxy throwable) {

    static PendingLog from(ILoggingEvent event) {
        return new PendingLog(Instant.ofEpochMilli(event.getTimeStamp()), event.getLevel(),
                event.getLoggerName(), event.getThreadName(), event.getFormattedMessage(),
                event.getThrowableProxy());
    }
}
