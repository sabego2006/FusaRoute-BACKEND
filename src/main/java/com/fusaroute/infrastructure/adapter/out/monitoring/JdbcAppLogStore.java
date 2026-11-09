package com.fusaroute.infrastructure.adapter.out.monitoring;

import com.fusaroute.domain.model.AppLog;
import com.fusaroute.domain.model.LogLevel;
import com.fusaroute.domain.port.out.AppLogQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a {@code app_logs} con el {@link JdbcTemplate} de la aplicacion, que usa el
 * MISMO pool de Hikari que JPA (SCRUM-186). No abre conexiones aparte: con el pooler de
 * sesion de Supabase las conexiones son pocas y una de mas para logs le quitaria una a
 * las peticiones. El SQL viene ya armado de {@link MonitoringTables}.
 */
@Component
class JdbcAppLogStore implements AppLogQueryPort {

    private final JdbcTemplate jdbc;
    private final MonitoringTables tables;

    JdbcAppLogStore(JdbcTemplate jdbc, MonitoringTables tables) {
        this.jdbc = jdbc;
        this.tables = tables;
    }

    /** Inserta un lote en una sola ida a la base. Lo llama el hilo escritor. */
    void insertBatch(List<LogEntry> entries, String profile) {
        jdbc.batchUpdate(tables.insertAppLog(), entries, entries.size(), (ps, e) -> {
            ps.setTimestamp(1, Timestamp.from(e.ts()));
            ps.setString(2, e.level().name());
            ps.setString(3, e.logger());
            ps.setString(4, e.message());
            ps.setString(5, e.exception());
            ps.setString(6, e.thread());
            ps.setString(7, profile);
        });
    }

    @Override
    public List<AppLog> findRecent(LogLevel level, Instant since, int limit) {
        List<Object> args = new ArrayList<>();
        if (level != null) {
            args.add(level.name());
        }
        if (since != null) {
            args.add(Timestamp.from(since));
        }
        args.add(limit);

        return jdbc.query(tables.selectAppLogs(level != null, since != null), (rs, i) -> new AppLog(
                rs.getLong("id"),
                rs.getTimestamp("ts").toInstant(),
                LogLevel.valueOf(rs.getString("level")),
                rs.getString("logger"),
                rs.getString("message"),
                rs.getString("exception"),
                rs.getString("thread"),
                rs.getString("profile")), args.toArray());
    }

    /** Borra lo anterior a {@code cutoff}; devuelve cuantas filas. */
    int deleteOlderThan(Instant cutoff) {
        return jdbc.update(tables.deleteAppLogsOlderThan(), Timestamp.from(cutoff));
    }
}
