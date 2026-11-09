package com.fusaroute.infrastructure.adapter.out.monitoring;

import com.fusaroute.domain.model.AppLog;
import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.LogLevel;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba sin base: que cada almacen arme la sentencia correcta y le ponga los parametros
 * en el orden correcto. Que el SQL funcione de verdad lo prueba {@code MonitoringStoresIT}
 * contra Postgres; este test existe porque corre en cualquier maquina, con o sin Docker.
 */
class MonitoringStoresTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T12:00:00Z");

    /** JdbcTemplate que solo anota lo que le piden, sin conexion. */
    private static final class Grabadora extends JdbcTemplate {
        String sql;
        Object[] args;
        RowMapper<?> mapper;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.args = args;
            this.mapper = rowMapper;
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.args = args;
            return 3;
        }

        @Override
        public <T> int[][] batchUpdate(String sql, Collection<T> batchArgs, int batchSize,
                                       ParameterizedPreparedStatementSetter<T> setter) {
            this.sql = sql;
            this.args = new Object[] {batchSize};
            for (T item : batchArgs) {
                try {
                    setter.setValues(preparada, item);
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
            return new int[0][];
        }

        final PreparedStatement preparada = mock(PreparedStatement.class);
    }

    // --- MonitoringTables -------------------------------------------------------------

    @Test
    void el_sql_sale_calificado_con_el_esquema() {
        MonitoringTables tablas = new MonitoringTables("fusaroute");

        assertThat(tablas.insertAppLog()).contains("INSERT INTO fusaroute.app_logs");
        assertThat(tablas.deleteAppLogsOlderThan()).isEqualTo("DELETE FROM fusaroute.app_logs WHERE ts < ?");
        assertThat(tablas.insertEvent()).contains("INSERT INTO fusaroute.search_events");
        assertThat(tablas.deleteEventsOlderThan()).isEqualTo("DELETE FROM fusaroute.search_events WHERE ts < ?");
        assertThat(tablas.schema()).isEqualTo("fusaroute");
    }

    @Test
    void cada_combinacion_de_filtros_es_una_sentencia_fija_con_sus_parametros() {
        MonitoringTables tablas = new MonitoringTables("public");

        assertThat(tablas.selectAppLogs(false, false))
                .endsWith("FROM public.app_logs ORDER BY ts DESC, id DESC LIMIT ?");
        assertThat(tablas.selectAppLogs(true, false)).contains("WHERE level = ? ORDER BY");
        assertThat(tablas.selectAppLogs(false, true)).contains("WHERE ts >= ? ORDER BY");
        assertThat(tablas.selectAppLogs(true, true)).contains("WHERE level = ? AND ts >= ? ORDER BY");
        assertThat(tablas.selectEvents(false)).endsWith("FROM public.search_events ORDER BY ts DESC, id DESC LIMIT ?");
        assertThat(tablas.selectEvents(true)).contains("WHERE ts >= ? ORDER BY");
    }

    @Test
    void un_esquema_que_no_es_un_identificador_simple_se_rechaza() {
        assertThatThrownBy(() -> new MonitoringTables("fusaroute; DROP TABLE users"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonitoringTables("a.b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonitoringTables("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonitoringTables(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- JdbcAppLogStore --------------------------------------------------------------

    @Test
    void el_lote_de_logs_pone_los_parametros_en_el_orden_de_la_sentencia() throws Exception {
        Grabadora jdbc = new Grabadora();
        JdbcAppLogStore store = new JdbcAppLogStore(jdbc, new MonitoringTables("public"));

        store.insertBatch(List.of(new LogEntry(AHORA, LogLevel.WARN, "log.X", "mensaje", null, "main")), "dev");

        assertThat(jdbc.sql).contains("INSERT INTO public.app_logs");
        assertThat(jdbc.args).containsExactly(1);
        verify(jdbc.preparada).setTimestamp(1, Timestamp.from(AHORA));
        verify(jdbc.preparada).setString(2, "WARN");
        verify(jdbc.preparada).setString(3, "log.X");
        verify(jdbc.preparada).setString(4, "mensaje");
        verify(jdbc.preparada).setString(5, null);
        verify(jdbc.preparada).setString(6, "main");
        verify(jdbc.preparada).setString(7, "dev");
    }

    @Test
    void la_consulta_de_logs_manda_nivel_fecha_y_limite_en_ese_orden() {
        Grabadora jdbc = new Grabadora();
        JdbcAppLogStore store = new JdbcAppLogStore(jdbc, new MonitoringTables("public"));

        store.findRecent(LogLevel.ERROR, AHORA, 25);

        assertThat(jdbc.sql).contains("WHERE level = ? AND ts >= ? ORDER BY");
        assertThat(jdbc.args).containsExactly("ERROR", Timestamp.from(AHORA), 25);

        store.findRecent(null, null, 100);

        assertThat(jdbc.sql).doesNotContain("WHERE");
        assertThat(jdbc.args).containsExactly(100);
    }

    @Test
    void la_fila_de_app_logs_se_convierte_en_un_app_log() throws Exception {
        Grabadora jdbc = new Grabadora();
        new JdbcAppLogStore(jdbc, new MonitoringTables("public")).findRecent(null, null, 1);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(5L);
        when(rs.getTimestamp("ts")).thenReturn(Timestamp.from(AHORA));
        when(rs.getString("level")).thenReturn("ERROR");
        when(rs.getString("logger")).thenReturn("log.X");
        when(rs.getString("message")).thenReturn("m");
        when(rs.getString("exception")).thenReturn("pila");
        when(rs.getString("thread")).thenReturn("main");
        when(rs.getString("profile")).thenReturn("prod");

        Object fila = jdbc.mapper.mapRow(rs, 0);

        assertThat(fila).isEqualTo(new AppLog(5L, AHORA, LogLevel.ERROR, "log.X", "m", "pila", "main", "prod"));
    }

    @Test
    void la_purga_de_logs_manda_el_corte_y_devuelve_las_filas_borradas() {
        Grabadora jdbc = new Grabadora();

        int borradas = new JdbcAppLogStore(jdbc, new MonitoringTables("public")).deleteOlderThan(AHORA);

        assertThat(borradas).isEqualTo(3);
        assertThat(jdbc.args).containsExactly(Timestamp.from(AHORA));
    }

    // --- JdbcUsageEventStore ------------------------------------------------------------

    @Test
    void el_lote_de_eventos_manda_los_nulos_como_null_de_sql() throws Exception {
        Grabadora jdbc = new Grabadora();
        JdbcUsageEventStore store = new JdbcUsageEventStore(jdbc, new MonitoringTables("public"));

        store.insertBatch(List.of(UsageEvent.of(AHORA, UsageEventType.BOARDED, null, null, null,
                SearchOutcome.NOT_FOUND, null, null, false)));

        verify(jdbc.preparada).setString(2, "BOARDED");
        verify(jdbc.preparada).setNull(5, Types.BIGINT);
        verify(jdbc.preparada).setString(6, "NOT_FOUND");
        verify(jdbc.preparada).setString(7, null);
        verify(jdbc.preparada).setNull(8, Types.INTEGER);
        verify(jdbc.preparada).setBoolean(9, false);
    }

    @Test
    void el_lote_de_eventos_manda_los_valores_presentes_y_recorta_los_rotulos() throws Exception {
        Grabadora jdbc = new Grabadora();
        JdbcUsageEventStore store = new JdbcUsageEventStore(jdbc, new MonitoringTables("public"));

        store.insertBatch(List.of(UsageEvent.of(AHORA, UsageEventType.SEARCH, "C".repeat(300), "Terminal", 9L,
                SearchOutcome.FOUND, EtaSource.TRAFFIC, 420, true)));

        verify(jdbc.preparada).setString(3, LogSanitizer.truncate("C".repeat(300), JdbcUsageEventStore.MAX_LABEL));
        verify(jdbc.preparada).setString(4, "Terminal");
        verify(jdbc.preparada).setLong(5, 9L);
        verify(jdbc.preparada).setString(7, "TRAFFIC");
        verify(jdbc.preparada).setInt(8, 420);
        verify(jdbc.preparada).setBoolean(9, true);
    }

    @Test
    void la_consulta_de_eventos_manda_la_fecha_solo_si_la_hay() {
        Grabadora jdbc = new Grabadora();
        JdbcUsageEventStore store = new JdbcUsageEventStore(jdbc, new MonitoringTables("public"));

        store.findRecent(AHORA, 7);
        assertThat(jdbc.args).containsExactly(Timestamp.from(AHORA), 7);

        store.findRecent(null, 7);
        assertThat(jdbc.args).containsExactly(7);
    }

    @Test
    void la_fila_de_search_events_se_convierte_en_un_evento_con_sus_nulos() throws Exception {
        Grabadora jdbc = new Grabadora();
        new JdbcUsageEventStore(jdbc, new MonitoringTables("public")).findRecent(null, 1);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(8L);
        when(rs.getTimestamp("ts")).thenReturn(Timestamp.from(AHORA));
        when(rs.getString("event_type")).thenReturn("SEARCH");
        when(rs.getString("origin_label")).thenReturn("Centro");
        when(rs.getString("destination_label")).thenReturn("Terminal");
        when(rs.getLong("route_id")).thenReturn(0L);
        when(rs.getInt("latency_ms")).thenReturn(0);
        when(rs.wasNull()).thenReturn(true);
        when(rs.getString("outcome")).thenReturn("LONG_WALK");
        when(rs.getString("eta_source")).thenReturn(null);
        when(rs.getBoolean("congestion")).thenReturn(true);

        UsageEvent evento = (UsageEvent) jdbc.mapper.mapRow(rs, 0);

        assertThat(evento.id()).isEqualTo(8L);
        assertThat(evento.routeId()).isNull();
        assertThat(evento.latencyMs()).isNull();
        assertThat(evento.etaSource()).isNull();
        assertThat(evento.outcome()).isEqualTo(SearchOutcome.LONG_WALK);
        assertThat(evento.congestion()).isTrue();
    }

    @Test
    void la_purga_de_eventos_manda_el_corte() {
        Grabadora jdbc = new Grabadora();

        new JdbcUsageEventStore(jdbc, new MonitoringTables("public")).deleteOlderThan(AHORA);

        assertThat(jdbc.sql).isEqualTo("DELETE FROM public.search_events WHERE ts < ?");
        assertThat(jdbc.args).containsExactly(Timestamp.from(AHORA));
    }
}
