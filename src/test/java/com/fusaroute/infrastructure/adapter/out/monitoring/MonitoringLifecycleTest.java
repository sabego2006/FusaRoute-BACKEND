package com.fusaroute.infrastructure.adapter.out.monitoring;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.LogLevel;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El arranque de SCRUM-186 sin base: que al estar listo el contexto se conecte el
 * appender de Logback, se escriba la fila INFO, se purgue y se publiquen los contadores.
 * Usa el Logback global del JVM (es el que busca {@code MonitoringLifecycle}) y lo deja
 * como estaba.
 */
class MonitoringLifecycleTest {

    private final JdbcAppLogStore logStore = mock(JdbcAppLogStore.class);
    private final JdbcUsageEventStore eventStore = mock(JdbcUsageEventStore.class);
    private final MonitoringDataPurger purger = mock(MonitoringDataPurger.class);
    private final AsyncUsageEventRecorder recorder = new AsyncUsageEventRecorder(10, 5, 20);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MockEnvironment environment = new MockEnvironment();
    @SuppressWarnings("unchecked")
    private final ObjectProvider<BuildProperties> build = mock(ObjectProvider.class);

    private Logger root;
    private DatabaseLogAppender appender;
    private ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> previo;
    private MonitoringLifecycle lifecycle;

    @BeforeEach
    void preparaLogbackYElEntorno() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        // El JVM de surefire es compartido: otros tests levantan contextos de Spring completos
        // y dejan su propio DB_LOGS en el root. Se aparta mientras corre este test.
        previo = root.getAppender(DatabaseLogAppender.APPENDER_NAME);
        if (previo != null) {
            root.detachAppender(previo);
        }
        environment.setActiveProfiles("dev");
        environment.setProperty("RENDER_GIT_COMMIT", "abc123");
        lifecycle = new MonitoringLifecycle(logStore, eventStore, recorder, purger, meters, environment, build);
    }

    @AfterEach
    void restauraLogback() {
        lifecycle.onShutdown();
        if (appender != null) {
            root.detachAppender(appender);
            appender.stop();
        }
        if (previo != null) {
            root.addAppender(previo);
        }
    }

    private void declaraElAppenderEnLogback() {
        appender = new DatabaseLogAppender();
        appender.setContext(root.getLoggerContext());
        appender.setName(DatabaseLogAppender.APPENDER_NAME);
        appender.setFlushIntervalMs(20);
        appender.start();
        root.addAppender(appender);
    }

    @Test
    @SuppressWarnings("unchecked")
    void al_estar_listo_deja_la_fila_info_con_perfil_y_commit_purga_y_publica_contadores() {
        declaraElAppenderEnLogback();

        lifecycle.onReady();

        ArgumentCaptor<List<LogEntry>> lote = ArgumentCaptor.forClass(List.class);
        verify(logStore, timeout(3_000)).insertBatch(lote.capture(), eq("dev"));
        LogEntry arranque = lote.getValue().stream()
                .filter(fila -> fila.level() == LogLevel.INFO).findFirst().orElseThrow();
        assertThat(arranque.message()).startsWith("FusaRoute iniciado")
                .contains("perfil=dev").contains("commit=abc123").contains("version=desconocida");
        verify(purger).purgeNow();
        assertThat(meters.find("fusaroute.monitoring.dropped").tag("queue", "app_logs").functionCounter())
                .isNotNull();
        assertThat(meters.find("fusaroute.monitoring.dropped").tag("queue", "search_events").functionCounter())
                .isNotNull();
        assertThat(meters.find("fusaroute.monitoring.queue.size").tag("queue", "app_logs").gauge()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void la_version_sale_de_build_properties_cuando_existe() {
        declaraElAppenderEnLogback();
        Properties props = new Properties();
        props.setProperty("version", "1.4.0");
        when(build.getIfAvailable()).thenReturn(new BuildProperties(props));

        lifecycle.onReady();

        ArgumentCaptor<List<LogEntry>> lote = ArgumentCaptor.forClass(List.class);
        verify(logStore, timeout(3_000)).insertBatch(lote.capture(), eq("dev"));
        assertThat(lote.getValue()).anyMatch(fila -> fila.message().contains("version=1.4.0"));
    }

    @Test
    void sin_appender_en_logback_igual_arranca_el_registrador_y_purga() {
        // El Logback del JVM no trae DB_LOGS: es el caso de otro sistema de logging.
        environment.setActiveProfiles();

        lifecycle.onReady();

        verify(purger).purgeNow();
        recorder.record(UsageEvent.of(Instant.parse("2026-11-08T12:00:00Z"), UsageEventType.SEARCH, "A", "B", 1L,
                SearchOutcome.FOUND, EtaSource.ESTIMATED, 5, false));
        verify(eventStore, timeout(3_000)).insertBatch(org.mockito.ArgumentMatchers.anyList());
        assertThat(meters.find("fusaroute.monitoring.dropped").tag("queue", "app_logs").functionCounter()).isNull();
    }

    @Test
    void al_cerrar_el_contexto_el_appender_vuelve_a_acumular_en_memoria() {
        declaraElAppenderEnLogback();
        lifecycle.onReady();
        verify(logStore, timeout(3_000)).insertBatch(org.mockito.ArgumentMatchers.anyList(), eq("dev"));

        lifecycle.onShutdown();
        org.slf4j.Logger prueba = LoggerFactory.getLogger("com.fusaroute.prueba.Lifecycle");
        prueba.warn("llega despues del cierre");

        assertThat(appender.pendingCount()).isEqualTo(1);
    }
}
