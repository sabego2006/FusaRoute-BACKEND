package com.fusaroute.infrastructure.adapter.out.monitoring;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.fusaroute.domain.model.LogLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * El appender con un LoggerContext propio, sin Spring ni base: lo que se prueba es la
 * mecanica (nivel, bufer previo al arranque, bucle, sanitizacion, cola llena), y el
 * "sink" es una lista en memoria.
 */
class DatabaseLogAppenderTest {

    private static final String JWT =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3Iiwicm9sZSI6IkFETUlOIn0.c2lnbmF0dXJlRkFLRQ";

    private LoggerContext context;
    private Logger app;
    private DatabaseLogAppender appender;
    private final List<LogEntry> escritas = new CopyOnWriteArrayList<>();

    @BeforeEach
    void creaElAppender() {
        context = new LoggerContext();
        appender = new DatabaseLogAppender();
        appender.setContext(context);
        appender.setQueueSize(50);
        appender.setBatchSize(10);
        appender.setFlushIntervalMs(20);
        appender.setName(DatabaseLogAppender.APPENDER_NAME);
        appender.start();
        app = context.getLogger("com.fusaroute.prueba");
        app.addAppender(appender);
    }

    @AfterEach
    void detiene() {
        appender.stop();
        context.stop();
    }

    private Consumer<List<LogEntry>> guardaEnLista() {
        return escritas::addAll;
    }

    @Test
    void guarda_warn_y_error_e_ignora_info_y_debug() {
        appender.attach(guardaEnLista());

        app.debug("no");
        app.info("tampoco");
        app.warn("si, warn");
        app.error("si, error");

        await().atMost(2, TimeUnit.SECONDS).until(() -> escritas.size() == 2);
        assertThat(escritas).extracting(LogEntry::level).containsExactly(LogLevel.WARN, LogLevel.ERROR);
        assertThat(escritas).extracting(LogEntry::message).containsExactly("si, warn", "si, error");
        assertThat(escritas.getFirst().logger()).isEqualTo("com.fusaroute.prueba");
    }

    @Test
    void los_warn_previos_al_arranque_no_se_pierden() {
        // Antes de attach() no hay escritor: el contexto de Spring todavia no esta listo.
        app.warn("warn del arranque 1");
        app.error("error del arranque 2");
        assertThat(escritas).isEmpty();

        appender.attach(guardaEnLista());

        await().atMost(2, TimeUnit.SECONDS).until(() -> escritas.size() == 2);
        assertThat(escritas).extracting(LogEntry::message)
                .containsExactly("warn del arranque 1", "error del arranque 2");
    }

    @Test
    void el_bufer_previo_al_arranque_es_acotado() {
        for (int i = 0; i < 80; i++) {
            app.warn("w{}", i);
        }

        assertThat(appender.pendingCount()).isEqualTo(50);
        assertThat(appender.droppedCount()).isEqualTo(30);
    }

    @Test
    void la_excepcion_se_guarda_recortada() {
        appender.attach(guardaEnLista());

        app.error("fallo", new IllegalStateException("x".repeat(6_000)));

        await().atMost(2, TimeUnit.SECONDS).until(() -> escritas.size() == 1);
        String pila = escritas.getFirst().exception();
        assertThat(pila).isNotNull().hasSize(LogEntry.MAX_EXCEPTION).startsWith("java.lang.IllegalStateException");
    }

    @Test
    void un_mensaje_largo_se_recorta_a_2000() {
        appender.attach(guardaEnLista());

        app.warn("m".repeat(5_000));

        await().atMost(2, TimeUnit.SECONDS).until(() -> escritas.size() == 1);
        assertThat(escritas.getFirst().message()).hasSize(LogEntry.MAX_MESSAGE);
    }

    @Test
    void los_secretos_no_llegan_al_sink_ni_en_el_mensaje_ni_en_la_pila() {
        appender.attach(guardaEnLista());

        app.warn("Authorization: Bearer {} password=Secreta123", JWT);
        app.error("fallo con token", new RuntimeException("jwt=" + JWT + " key=AIzaSyD-FAKEFAKEFAKEFAKEFAKEFAKE1234567"));

        await().atMost(2, TimeUnit.SECONDS).until(() -> escritas.size() == 2);
        for (LogEntry fila : escritas) {
            assertThat(fila.message()).doesNotContain("eyJ").doesNotContain("Secreta123");
            if (fila.exception() != null) {
                assertThat(fila.exception()).doesNotContain("eyJ").doesNotContain("AIza");
            }
        }
    }

    @Test
    void lo_que_loguea_el_hilo_escritor_no_genera_filas_ni_bucle() throws Exception {
        // El sink hace lo que haria JDBC: loguear un WARN desde el hilo escritor. Si el
        // appender no lo ignorara, cada lote fabricaria una fila nueva, sin fin.
        CountDownLatch lotes = new CountDownLatch(1);
        appender.attach(batch -> {
            escritas.addAll(batch);
            app.warn("Hikari: conexion lenta (emitido desde el hilo escritor)");
            lotes.countDown();
        });

        app.warn("el unico log real");

        assertThat(lotes.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
        assertThat(escritas).extracting(LogEntry::message).containsExactly("el unico log real");
    }

    @Test
    void un_fallo_al_escribir_descarta_el_lote_cuenta_y_el_escritor_sigue() {
        CountDownLatch primero = new CountDownLatch(1);
        appender.attach(batch -> {
            if (primero.getCount() > 0) {
                primero.countDown();
                throw new IllegalStateException("base caida");
            }
            escritas.addAll(batch);
        });

        app.warn("se pierde");
        await().atMost(2, TimeUnit.SECONDS).until(() -> appender.droppedCount() == 1);

        app.warn("se guarda");
        await().atMost(2, TimeUnit.SECONDS).until(() -> escritas.size() == 1);
        assertThat(escritas.getFirst().message()).isEqualTo("se guarda");
    }

    @Test
    void una_rafaga_de_200_warn_no_frena_a_quien_loguea_y_el_exceso_se_descarta_y_se_cuenta() {
        CountDownLatch atascado = new CountDownLatch(1);
        // La base no contesta: el escritor se queda con su primer lote en la mano.
        appender.attach(batch -> {
            try {
                atascado.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long inicio = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            app.warn("rafaga {}", i);
        }
        long milisegundos = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

        // Cola de 50 + un lote de hasta 10 en el escritor: el resto (>= 140) no cabe.
        assertThat(milisegundos).isLessThan(1_000);
        assertThat(appender.droppedCount()).isGreaterThanOrEqualTo(200 - 50 - 10);
        assertThat(appender.pendingCount()).isLessThanOrEqualTo(50);
        atascado.countDown();
    }

    @Test
    void la_fila_de_arranque_entra_aunque_sea_info() {
        appender.attach(guardaEnLista());

        appender.enqueue(new PendingLog(java.time.Instant.now(), ch.qos.logback.classic.Level.INFO,
                "com.fusaroute.Arranque", "main", "FusaRoute iniciado | perfil=dev", null));

        await().atMost(2, TimeUnit.SECONDS).until(() -> escritas.size() == 1);
        assertThat(escritas.getFirst().level()).isEqualTo(LogLevel.INFO);
        assertThat(escritas.getFirst().message()).contains("FusaRoute iniciado");
    }
}
