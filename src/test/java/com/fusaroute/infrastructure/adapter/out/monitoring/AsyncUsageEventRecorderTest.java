package com.fusaroute.infrastructure.adapter.out.monitoring;

import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Demuestra el contrato de {@code UsageEventRecorderPort}: registrar un evento no
 * bloquea al llamador, aunque la base este atascada (SCRUM-186).
 */
class AsyncUsageEventRecorderTest {

    private AsyncUsageEventRecorder recorder;

    @AfterEach
    void detiene() {
        if (recorder != null) {
            recorder.stop();
        }
    }

    private static UsageEvent evento(int i) {
        return UsageEvent.of(Instant.parse("2026-11-08T12:00:00Z"), UsageEventType.SEARCH,
                "Centro " + i, "Terminal", 7L, SearchOutcome.FOUND, EtaSource.TRAFFIC, 120, false);
    }

    @Test
    void registrar_no_bloquea_al_llamador_aunque_el_escritor_este_atascado() throws Exception {
        recorder = new AsyncUsageEventRecorder(10, 5, 20);
        CountDownLatch atascado = new CountDownLatch(1);
        // La "base" nunca contesta: el hilo escritor se queda dentro del primer lote.
        recorder.start(batch -> {
            try {
                atascado.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long inicio = System.nanoTime();
        for (int i = 0; i < 5_000; i++) {
            recorder.record(evento(i));
        }
        Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);

        // 5.000 llamadas contra un escritor muerto: si alguna esperara a la base, esto
        // tardaria los 30 s del latch. Un segundo es un margen enorme para una cola en memoria.
        assertThat(duracion).isLessThan(Duration.ofSeconds(1));
        // Cola de 10 + el lote que el escritor tiene en la mano (hasta 5); el resto se descarta y se cuenta.
        assertThat(recorder.droppedCount()).isGreaterThanOrEqualTo(5_000 - 10 - 5);
        assertThat(recorder.pendingCount()).isLessThanOrEqualTo(10);
        atascado.countDown();
    }

    @Test
    void registrar_no_lanza_aunque_el_escritor_falle() {
        recorder = new AsyncUsageEventRecorder(10, 5, 20);
        recorder.start(batch -> {
            throw new IllegalStateException("base caida");
        });

        recorder.record(evento(1));

        await().atMost(2, TimeUnit.SECONDS).until(() -> recorder.droppedCount() == 1);
    }

    @Test
    void un_evento_nulo_se_ignora_sin_lanzar() {
        recorder = new AsyncUsageEventRecorder(10, 5, 20);

        recorder.record(null);

        assertThat(recorder.pendingCount()).isZero();
    }

    @Test
    void los_eventos_anteriores_al_arranque_esperan_y_salen_al_arrancar() {
        recorder = new AsyncUsageEventRecorder(10, 5, 20);
        List<UsageEvent> escritos = new CopyOnWriteArrayList<>();
        recorder.record(evento(1));
        recorder.record(evento(2));
        assertThat(escritos).isEmpty();

        recorder.start(escritos::addAll);

        await().atMost(2, TimeUnit.SECONDS).until(() -> escritos.size() == 2);
    }

    @Test
    void escribe_por_lotes_del_tamano_configurado() {
        recorder = new AsyncUsageEventRecorder(100, 4, 20);
        List<Integer> tamanos = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 10; i++) {
            recorder.record(evento(i));
        }

        recorder.start(batch -> tamanos.add(batch.size()));

        await().atMost(2, TimeUnit.SECONDS).until(() -> tamanos.stream().mapToInt(Integer::intValue).sum() == 10);
        assertThat(tamanos).allMatch(t -> t <= 4);
    }
}
