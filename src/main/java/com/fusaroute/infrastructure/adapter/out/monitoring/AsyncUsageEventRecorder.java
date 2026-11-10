package com.fusaroute.infrastructure.adapter.out.monitoring;

import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.port.out.UsageEventRecorderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Adaptador asincrono de {@link UsageEventRecorderPort} (SCRUM-186): encola y vuelve.
 * El hilo escritor de {@link AsyncBatchWriter} inserta por lotes en {@code search_events}.
 *
 * <p>La garantia del puerto —el llamador no espera ni falla— se sostiene aqui con tres
 * decisiones: la cola es acotada, {@code record} usa {@code offer} (no {@code put}) y
 * cualquier excepcion se traga y se cuenta. Un evento perdido aparece en el contador
 * {@code fusaroute.monitoring.dropped{queue="search_events"}}.
 */
@Component
class AsyncUsageEventRecorder implements UsageEventRecorderPort {

    static final String WRITER_THREAD_NAME = "fusaroute-search-events-writer";

    private final AsyncBatchWriter<UsageEvent> writer;
    private final FailureReporter failures = new FailureReporter("search_events");

    AsyncUsageEventRecorder(
            @Value("${app.logs.queue-size:1000}") int queueSize,
            @Value("${app.logs.batch-size:50}") int batchSize,
            @Value("${app.logs.flush-interval-ms:1000}") long flushIntervalMs) {
        this.writer = new AsyncBatchWriter<>(WRITER_THREAD_NAME, queueSize, batchSize, flushIntervalMs);
    }

    @Override
    public void record(UsageEvent event) {
        // offer() no bloquea ni lanza; el catch es por si el contrato cambia algun dia.
        try {
            writer.offer(event);
        } catch (RuntimeException ignored) {
            // Se descarta a proposito: nada que un evento de uso pueda hacerle a la peticion.
        }
    }

    /** Empieza a vaciar la cola hacia {@code sink}. Antes de esto los eventos esperan en memoria. */
    void start(Consumer<List<UsageEvent>> sink) {
        writer.start(sink, failures::report);
    }

    void stop() {
        writer.stop(2_000);
    }

    long droppedCount() {
        return writer.dropped();
    }

    int pendingCount() {
        return writer.pending();
    }
}
