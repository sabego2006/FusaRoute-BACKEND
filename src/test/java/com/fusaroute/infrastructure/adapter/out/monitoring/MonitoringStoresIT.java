package com.fusaroute.infrastructure.adapter.out.monitoring;

import com.fusaroute.domain.model.AppLog;
import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.LogLevel;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los almacenes JDBC de monitoreo contra un Postgres real (SCRUM-186): que la V24 aplique,
 * que el SQL del escritor y de las consultas funcione de verdad, y que la purga borre solo
 * lo viejo en las dos tablas. Un mock de JdbcTemplate no puede probar nada de esto.
 *
 * <p>Flyway aplica aqui el historial completo, asi que las tablas son las de la V24 y no
 * unas fabricadas por Hibernate. {@code disabledWithoutDocker = true}, igual que los demas IT.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcAppLogStore.class, JdbcUsageEventStore.class, MonitoringStoresIT.Config.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MonitoringStoresIT {

    private static final Instant AHORA = Instant.parse("2026-11-08T12:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        MonitoringTables monitoringTables() {
            return new MonitoringTables("public");
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcAppLogStore logs;

    @Autowired
    private JdbcUsageEventStore events;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Sin transaccion de prueba (ver {@code @Transactional(NOT_SUPPORTED)}): en Postgres una
     * sentencia fallida deja abortada la transaccion entera, y un test del caso "una tabla
     * falla y la otra se purga" no podria seguir. Como no hay rollback, cada test arranca
     * vaciando las tablas.
     */
    @BeforeEach
    void vaciaLasTablas() {
        jdbc.execute("TRUNCATE app_logs, search_events");
    }

    private static LogEntry log(Instant ts, LogLevel level, String message) {
        return new LogEntry(ts, level, "com.fusaroute.Prueba", message, null, "main");
    }

    private static UsageEvent evento(Instant ts, String origen, SearchOutcome resultado) {
        return UsageEvent.of(ts, UsageEventType.SEARCH, origen, "Terminal", 7L, resultado,
                EtaSource.TRAFFIC, 350, true);
    }

    // --- esquema ------------------------------------------------------------------

    @Test
    void la_v24_crea_las_tablas_y_los_indices_por_ts() {
        List<String> indices = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                 WHERE tablename IN ('app_logs', 'search_events')
                """, String.class);

        assertThat(indices).contains("ix_app_logs_ts", "ix_app_logs_level_ts", "ix_search_events_ts");
    }

    @Test
    void search_events_no_guarda_ip_ni_usuario() {
        List<String> columnas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_name = 'search_events'
                """, String.class);

        assertThat(columnas).isNotEmpty()
                .noneMatch(c -> c.contains("ip") || c.contains("user") || c.contains("email"));
    }

    @Test
    void la_base_rechaza_un_nivel_o_un_resultado_fuera_del_conjunto() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_logs (level, logger, message) VALUES ('FATAL', 'x', 'm')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO search_events (event_type, outcome) VALUES ('SEARCH', 'QUIZAS')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- app_logs -----------------------------------------------------------------

    @Test
    void guarda_un_lote_de_logs_y_los_lee_del_mas_reciente_al_mas_antiguo() {
        logs.insertBatch(List.of(
                log(AHORA.minusSeconds(60), LogLevel.WARN, "viejo"),
                log(AHORA, LogLevel.ERROR, "nuevo"),
                log(AHORA.minusSeconds(30), LogLevel.INFO, "medio")), "dev");

        List<AppLog> leidos = logs.findRecent(null, null, 10);

        assertThat(leidos).extracting(AppLog::message).containsExactly("nuevo", "medio", "viejo");
        assertThat(leidos.getFirst().ts()).isEqualTo(AHORA);
        assertThat(leidos.getFirst().profile()).isEqualTo("dev");
        assertThat(leidos.getFirst().level()).isEqualTo(LogLevel.ERROR);
        assertThat(leidos.getFirst().thread()).isEqualTo("main");
    }

    @Test
    void filtra_por_nivel_exacto_por_fecha_y_respeta_el_limite() {
        logs.insertBatch(List.of(
                log(AHORA.minusSeconds(120), LogLevel.WARN, "w1"),
                log(AHORA.minusSeconds(60), LogLevel.ERROR, "e1"),
                log(AHORA, LogLevel.WARN, "w2")), "dev");

        assertThat(logs.findRecent(LogLevel.WARN, null, 10)).extracting(AppLog::message)
                .containsExactly("w2", "w1");
        assertThat(logs.findRecent(null, AHORA.minusSeconds(60), 10)).extracting(AppLog::message)
                .containsExactly("w2", "e1");
        assertThat(logs.findRecent(LogLevel.WARN, AHORA.minusSeconds(60), 10)).extracting(AppLog::message)
                .containsExactly("w2");
        assertThat(logs.findRecent(null, null, 1)).extracting(AppLog::message).containsExactly("w2");
    }

    @Test
    void acepta_un_mensaje_de_2000_y_una_pila_de_4000_con_caracteres_acentuados() {
        LogEntry fila = new LogEntry(AHORA, LogLevel.ERROR, "com.fusaroute.Prueba",
                "ñ".repeat(LogEntry.MAX_MESSAGE), "á".repeat(LogEntry.MAX_EXCEPTION), "http-nio-1");

        logs.insertBatch(List.of(fila), "prod");

        AppLog leido = logs.findRecent(null, null, 1).getFirst();
        assertThat(leido.message()).hasSize(2_000);
        assertThat(leido.exception()).hasSize(4_000);
    }

    @Test
    void una_excepcion_nula_se_guarda_como_nula() {
        logs.insertBatch(List.of(log(AHORA, LogLevel.WARN, "sin pila")), "dev");

        assertThat(logs.findRecent(null, null, 1).getFirst().exception()).isNull();
    }

    // --- search_events ---------------------------------------------------------------

    @Test
    void guarda_y_lee_eventos_de_uso_con_todos_sus_campos() {
        events.insertBatch(List.of(
                evento(AHORA.minusSeconds(10), "Centro", SearchOutcome.FOUND),
                evento(AHORA, "Pekin", SearchOutcome.LONG_WALK)));

        List<UsageEvent> leidos = events.findRecent(null, 10);

        assertThat(leidos).extracting(UsageEvent::originLabel).containsExactly("Pekin", "Centro");
        UsageEvent primero = leidos.getFirst();
        assertThat(primero.id()).isNotNull();
        assertThat(primero.occurredAt()).isEqualTo(AHORA);
        assertThat(primero.type()).isEqualTo(UsageEventType.SEARCH);
        assertThat(primero.destinationLabel()).isEqualTo("Terminal");
        assertThat(primero.routeId()).isEqualTo(7L);
        assertThat(primero.outcome()).isEqualTo(SearchOutcome.LONG_WALK);
        assertThat(primero.etaSource()).isEqualTo(EtaSource.TRAFFIC);
        assertThat(primero.latencyMs()).isEqualTo(350);
        assertThat(primero.congestion()).isTrue();
    }

    @Test
    void los_campos_opcionales_viajan_como_nulos() {
        events.insertBatch(List.of(UsageEvent.of(AHORA, UsageEventType.BOARDED, null, null, null,
                SearchOutcome.NOT_FOUND, null, null, false)));

        UsageEvent leido = events.findRecent(null, 1).getFirst();

        assertThat(leido.routeId()).isNull();
        assertThat(leido.etaSource()).isNull();
        assertThat(leido.latencyMs()).isNull();
        assertThat(leido.originLabel()).isNull();
        assertThat(leido.congestion()).isFalse();
    }

    @Test
    void un_rotulo_de_mas_de_120_se_recorta_y_no_tumba_el_lote() {
        events.insertBatch(List.of(
                evento(AHORA, "B".repeat(500), SearchOutcome.FOUND),
                evento(AHORA.minusSeconds(1), "Normal", SearchOutcome.FOUND)));

        List<UsageEvent> leidos = events.findRecent(null, 10);

        assertThat(leidos).hasSize(2);
        assertThat(leidos.getFirst().originLabel()).hasSize(JdbcUsageEventStore.MAX_LABEL);
    }

    @Test
    void filtra_los_eventos_por_fecha() {
        events.insertBatch(List.of(
                evento(AHORA.minusSeconds(100), "Viejo", SearchOutcome.FOUND),
                evento(AHORA, "Nuevo", SearchOutcome.FOUND)));

        assertThat(events.findRecent(AHORA.minusSeconds(50), 10)).extracting(UsageEvent::originLabel)
                .containsExactly("Nuevo");
    }

    // --- purga ------------------------------------------------------------------------

    @Test
    void la_purga_borra_lo_mayor_a_30_dias_en_las_dos_tablas_y_conserva_lo_reciente() {
        Instant hace40 = AHORA.minusSeconds(40L * 86_400);
        Instant hace29 = AHORA.minusSeconds(29L * 86_400);
        logs.insertBatch(List.of(
                log(hace40, LogLevel.WARN, "viejo"),
                log(hace29, LogLevel.WARN, "reciente")), "dev");
        events.insertBatch(List.of(
                evento(hace40, "Viejo", SearchOutcome.FOUND),
                evento(hace29, "Reciente", SearchOutcome.FOUND)));
        MonitoringDataPurger purger = new MonitoringDataPurger(logs, events,
                Clock.fixed(AHORA, ZoneOffset.UTC), 30);

        int borradas = purger.purgeNow();

        assertThat(borradas).isEqualTo(2);
        assertThat(logs.findRecent(null, null, 10)).extracting(AppLog::message).containsExactly("reciente");
        assertThat(events.findRecent(null, 10)).extracting(UsageEvent::originLabel).containsExactly("Reciente");
    }

    @Test
    void la_retencion_se_toma_de_la_propiedad() {
        logs.insertBatch(List.of(log(AHORA.minusSeconds(8L * 86_400), LogLevel.WARN, "de hace 8 dias")), "dev");
        MonitoringDataPurger sieteDias = new MonitoringDataPurger(logs, events,
                Clock.fixed(AHORA, ZoneOffset.UTC), 7);

        assertThat(sieteDias.purgeNow()).isEqualTo(1);
    }

    @Test
    void una_tabla_que_falla_al_purgar_no_impide_purgar_la_otra() {
        events.insertBatch(List.of(evento(AHORA.minusSeconds(40L * 86_400), "Viejo", SearchOutcome.FOUND)));
        // Esquema inexistente: el DELETE de app_logs revienta. El de search_events usa su propio store.
        JdbcAppLogStore roto = new JdbcAppLogStore(jdbc, new MonitoringTables("no_existe"));
        MonitoringDataPurger purger = new MonitoringDataPurger(roto, events,
                Clock.fixed(AHORA, ZoneOffset.UTC), 30);

        assertThat(purger.purgeNow()).isEqualTo(1);
    }

    @Test
    void la_retencion_menor_a_un_dia_es_un_error_de_configuracion() {
        assertThatThrownBy(() -> new MonitoringDataPurger(logs, events, Clock.systemUTC(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
