package com.fusaroute.infrastructure.adapter.out.monitoring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.List;
import java.util.function.Consumer;

/**
 * Appender de Logback que guarda en {@code app_logs} todo WARN y ERROR (SCRUM-186).
 *
 * <h3>Por que Logback lo crea y Spring lo "adopta" despues</h3>
 * Los primeros WARN de una aplicacion salen durante el arranque, antes de que exista el
 * contexto de Spring, el pool de Hikari o la tabla. Si el appender fuera un bean de
 * Spring, esos WARN se perderian justo cuando mas interesan (una variable mal puesta,
 * un banco de palabras que no cargo). Por eso lo declara {@code logback-spring.xml} y
 * desde el primer instante encola en memoria; cuando el contexto esta listo,
 * {@code MonitoringLifecycle} lo busca por nombre y le entrega el escritor con
 * {@link #attach}. La cola acotada hace de bufer previo al arranque.
 *
 * <h3>Lo que garantiza</h3>
 * <ul>
 *   <li><b>Nunca bloquea</b> al hilo que loguea: copiar el evento y encolarlo es todo lo
 *       que hace ahi. Cola llena = el evento se descarta y se cuenta.</li>
 *   <li><b>No se escribe a si mismo</b>: lo que el hilo escritor loguee (por ejemplo el
 *       WARN de Hikari porque la base esta caida) se ignora, o cada fallo de escritura
 *       fabricaria otra fila que fallaria igual, en bucle.</li>
 *   <li>La limpieza de secretos ocurre en el hilo escritor ({@link LogSanitizer}).</li>
 * </ul>
 */
public class DatabaseLogAppender extends AppenderBase<ILoggingEvent> {

    /** Nombre con el que {@code logback-spring.xml} lo declara y con el que se le busca. */
    public static final String APPENDER_NAME = "DB_LOGS";

    static final String WRITER_THREAD_NAME = "fusaroute-app-logs-writer";

    private int queueSize = 1_000;
    private int batchSize = 50;
    private long flushIntervalMs = 1_000;

    private AsyncBatchWriter<PendingLog> writer;
    private Consumer<List<LogEntry>> attached;
    private final FailureReporter failures = new FailureReporter("app_logs");

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    @Override
    public void start() {
        writer = new AsyncBatchWriter<>(WRITER_THREAD_NAME, queueSize, batchSize, flushIntervalMs);
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        if (writer != null) {
            writer.stop(2_000);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(Level.WARN)) {
            return;
        }
        // El hilo escritor ejecuta JDBC, y JDBC loguea. Ignorarlo corta el bucle.
        if (WRITER_THREAD_NAME.equals(event.getThreadName())) {
            return;
        }
        writer.offer(PendingLog.from(event));
    }

    /** Encola una fila sin pasar por el filtro de nivel; la usa la fila INFO de arranque. */
    void enqueue(PendingLog log) {
        writer.offer(log);
    }

    /**
     * Empieza a vaciar la cola hacia la base. Lo llama {@code MonitoringLifecycle} cuando
     * el contexto de Spring termino de arrancar y Flyway ya creo las tablas.
     *
     * @param sink recibe el lote ya convertido por {@link LogEntry#from}.
     */
    synchronized void attach(Consumer<List<LogEntry>> sink) {
        // Si ya habia un contexto conectado (varios contextos de Spring en un mismo JVM,
        // como en la suite de pruebas), gana el ultimo en llegar: Logback es global al JVM
        // y solo hay un appender.
        writer.stop(2_000);
        attached = sink;
        writer.start(
                batch -> sink.accept(batch.stream().map(LogEntry::from).toList()),
                failures::report);
    }

    /**
     * Deja de escribir y vuelve a acumular en memoria (cierre del contexto). Solo actua si
     * {@code sink} sigue siendo el conectado: un contexto viejo que cierra tarde no debe
     * desconectar al contexto nuevo.
     */
    synchronized void detach(Consumer<List<LogEntry>> sink) {
        if (attached == sink) {
            attached = null;
            writer.stop(2_000);
        }
    }

    long droppedCount() {
        return writer == null ? 0 : writer.dropped();
    }

    int pendingCount() {
        return writer == null ? 0 : writer.pending();
    }
}
