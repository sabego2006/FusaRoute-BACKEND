package com.fusaroute.infrastructure.adapter.out.monitoring;

/**
 * Avisa por la salida de error estandar que un lote no se pudo guardar, como mucho una
 * vez por minuto.
 *
 * <p>No usa SLF4J a proposito: el aviso de "no pude escribir logs" volveria a entrar
 * al appender de la base y, si la base sigue caida, fabricaria el mismo fallo que
 * intenta contar. La salida de error estandar va a la consola de Render sin pasar por
 * Logback.
 */
final class FailureReporter {

    private static final long INTERVAL_NANOS = 60_000_000_000L;

    private final String what;
    private long lastReport = System.nanoTime() - INTERVAL_NANOS;

    FailureReporter(String what) {
        this.what = what;
    }

    synchronized void report(Throwable cause) {
        long now = System.nanoTime();
        if (now - lastReport < INTERVAL_NANOS) {
            return;
        }
        lastReport = now;
        System.err.println("[fusaroute] No se pudo guardar un lote de " + what + ": "
                + LogSanitizer.sanitizeAndTruncate(String.valueOf(cause), 300));
    }
}
