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
 * las peticiones.
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
        jdbc.batchUpdate(
                "INSERT INTO " + tables.appLogs()
                        + " (ts, level, logger, message, exception, thread, profile) VALUES (?, ?, ?, ?, ?, ?, ?)",
                entries, entries.size(), (ps, e) -> {
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
        StringBuilder sql = new StringBuilder(
                "SELECT id, ts, level, logger, message, exception, thread, profile FROM " + tables.appLogs());
        List<Object> args = new ArrayList<>();
        String glue = " WHERE ";
        if (level != null) {
            sql.append(glue).append("level = ?");
            args.add(level.name());
            glue = " AND ";
        }
        if (since != null) {
            sql.append(glue).append("ts >= ?");
            args.add(Timestamp.from(since));
        }
        // id DESC desempata filas con el mismo ts, que un lote escribe a menudo.
        sql.append(" ORDER BY ts DESC, id DESC LIMIT ?");
        args.add(limit);

        return jdbc.query(sql.toString(), (rs, i) -> new AppLog(
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
        return jdbc.update("DELETE FROM " + tables.appLogs() + " WHERE ts < ?", Timestamp.from(cutoff));
    }
}
