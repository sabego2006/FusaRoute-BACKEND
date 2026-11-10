package com.fusaroute.infrastructure.adapter.out.monitoring;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Cola acotada con un hilo propio que la vacia por lotes (SCRUM-186). La comparten el
 * appender de logs y el registrador de eventos de uso porque ambos necesitan la misma
 * garantia: <b>quien produce nunca espera ni falla por la base</b>.
 *
 * <ul>
 *   <li>{@link #offer} no bloquea jamas: si la cola esta llena descarta el elemento y
 *       lo cuenta. Perder una estadistica es aceptable; frenar una peticion del usuario
 *       por una estadistica, no.</li>
 *   <li>Si el lote falla al escribirse (base caida o lenta) se descarta y se cuenta, no
 *       se reintenta: reintentar sin limite llenaria la cola igual y aplazaria el
 *       descarte sin evitarlo.</li>
 *   <li>Antes de {@link #start} la cola funciona como bufer acotado: lo que llegue
 *       durante el arranque espera ahi y sale cuando el contexto de Spring esta listo.</li>
 * </ul>
 */
final class AsyncBatchWriter<T> {

    private final BlockingQueue<T> queue;
    private final int batchSize;
    private final long flushIntervalMs;
    private final String threadName;
    private final AtomicLong dropped = new AtomicLong();

    private volatile boolean running;
    private Thread thread;
    private Consumer<List<T>> sink;
    private Consumer<Throwable> onFailure;

    AsyncBatchWriter(String threadName, int queueSize, int batchSize, long flushIntervalMs) {
        if (queueSize < 1 || batchSize < 1 || flushIntervalMs < 1) {
            throw new IllegalArgumentException("Tamano de cola, de lote e intervalo deben ser positivos");
        }
        this.threadName = threadName;
        this.queue = new ArrayBlockingQueue<>(queueSize);
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    /** Encola sin bloquear. {@code false} si no cupo (queda contado como descartado). */
    boolean offer(T item) {
        if (item == null) {
            return false;
        }
        if (queue.offer(item)) {
            return true;
        }
        dropped.incrementAndGet();
        return false;
    }

    /** Arranca el hilo escritor. Si ya estaba corriendo, no hace nada. */
    synchronized void start(Consumer<List<T>> sink, Consumer<Throwable> onFailure) {
        if (running) {
            return;
        }
        this.sink = sink;
        this.onFailure = onFailure;
        running = true;
        thread = new Thread(this::runLoop, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Detiene el hilo y vacia lo que quedaba, como mucho {@code waitMs}. Lo que no
     * alcance a salir se pierde; no vale la pena colgar el apagado por logs.
     */
    synchronized void stop(long waitMs) {
        if (!running) {
            return;
        }
        running = false;
        try {
            thread.join(waitMs);
            if (thread.isAlive()) {
                thread.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        thread = null;
        drainRemaining();
    }

    boolean isRunning() {
        return running;
    }

    long dropped() {
        return dropped.get();
    }

    int pending() {
        return queue.size();
    }

    private void runLoop() {
        while (running) {
            try {
                T first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first != null) {
                    write(takeBatch(first));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private List<T> takeBatch(T first) {
        List<T> batch = new ArrayList<>(batchSize);
        batch.add(first);
        queue.drainTo(batch, batchSize - 1);
        return batch;
    }

    private void drainRemaining() {
        T first;
        while ((first = queue.poll()) != null) {
            write(takeBatch(first));
        }
    }

    private void write(List<T> batch) {
        try {
            sink.accept(batch);
        } catch (RuntimeException e) {
            dropped.addAndGet(batch.size());
            onFailure.accept(e);
        }
    }
}
