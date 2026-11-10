package com.fusaroute.infrastructure.adapter.out.monitoring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * Pone en marcha el guardado en la base cuando el contexto de Spring esta listo
 * (SCRUM-186): conecta el appender de Logback con su escritor, arranca el registrador de
 * eventos de uso, deja la fila INFO de arranque, purga y publica los contadores.
 *
 * <p>Va en {@link ApplicationReadyEvent} y no antes porque Flyway ya termino ahi: antes
 * de ese punto la tabla puede no existir y el primer lote fallaria.
 */
@Component
class MonitoringLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MonitoringLifecycle.class);

    private final JdbcAppLogStore logStore;
    private final JdbcUsageEventStore eventStore;
    private final AsyncUsageEventRecorder recorder;
    private final MonitoringDataPurger purger;
    private final MeterRegistry meters;
    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildProperties;

    private DatabaseLogAppender appender;
    private Consumer<List<LogEntry>> sink;

    MonitoringLifecycle(JdbcAppLogStore logStore, JdbcUsageEventStore eventStore,
                        AsyncUsageEventRecorder recorder, MonitoringDataPurger purger,
                        MeterRegistry meters, Environment environment,
                        ObjectProvider<BuildProperties> buildProperties) {
        this.logStore = logStore;
        this.eventStore = eventStore;
        this.recorder = recorder;
        this.purger = purger;
        this.meters = meters;
        this.environment = environment;
        this.buildProperties = buildProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        String profile = activeProfile();

        recorder.start(eventStore::insertBatch);
        registerMeters("search_events", recorder,
                AsyncUsageEventRecorder::droppedCount, AsyncUsageEventRecorder::pendingCount);

        appender = findAppender();
        if (appender == null) {
            // Otro sistema de logging (por ejemplo en una prueba): sin appender no hay logs en la base.
            log.info("Sin DatabaseLogAppender en Logback: los logs no se guardan en la base");
        } else {
            sink = batch -> logStore.insertBatch(batch, profile);
            appender.attach(sink);
            registerMeters("app_logs", appender,
                    DatabaseLogAppender::droppedCount, DatabaseLogAppender::pendingCount);
            appender.enqueue(new PendingLog(Instant.now(), Level.INFO, MonitoringLifecycle.class.getName(),
                    Thread.currentThread().getName(), startupMessage(profile), null));
        }

        // Despues de enganchar el escritor: si la purga falla, su WARN ya llega a la base.
        purger.purgeNow();
    }

    @PreDestroy
    void onShutdown() {
        if (appender != null) {
            appender.detach(sink);
        }
        recorder.stop();
    }

    private String startupMessage(String profile) {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build != null ? build.getVersion() : "desconocida";
        // Render expone el commit desplegado; en local no existe y queda "desconocido".
        String commit = environment.getProperty("RENDER_GIT_COMMIT", "desconocido");
        return "FusaRoute iniciado | version=" + version + " | commit=" + commit + " | perfil=" + profile;
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }

    private static DatabaseLogAppender findAppender() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return null;
        }
        Appender<ILoggingEvent> found = context.getLogger(Logger.ROOT_LOGGER_NAME)
                .getAppender(DatabaseLogAppender.APPENDER_NAME);
        return found instanceof DatabaseLogAppender database ? database : null;
    }

    /**
     * El dueno ({@code owner}) es el objeto que se pasa a Micrometer, y no una lambda:
     * Micrometer guarda una referencia debil al objeto, y una lambda recien creada se
     * recolectaria y el contador dejaria de publicar en silencio.
     */
    private <T> void registerMeters(String queue, T owner, ToDoubleFunction<T> dropped,
                                    ToDoubleFunction<T> pending) {
        FunctionCounter.builder("fusaroute.monitoring.dropped", owner, dropped)
                .description("Eventos descartados por cola llena o por fallo al escribir en la base")
                .tag("queue", queue)
                .register(meters);
        Gauge.builder("fusaroute.monitoring.queue.size", owner, pending)
                .description("Eventos esperando a ser escritos en la base")
                .tag("queue", queue)
                .register(meters);
    }
}
