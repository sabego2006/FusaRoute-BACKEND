package com.fusaroute.infrastructure.adapter.out.monitoring;

import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.RouteSearchCount;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import com.fusaroute.domain.port.out.UsageEventQueryPort;
import com.fusaroute.domain.port.out.UsageStatsPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Acceso a {@code search_events}; mismo pool y mismas razones que {@link JdbcAppLogStore}. */
@Component
class JdbcUsageEventStore implements UsageEventQueryPort, UsageStatsPort {

    /** Ancho de origin_label y destination_label en la V24. */
    static final int MAX_LABEL = 120;

    private final JdbcTemplate jdbc;
    private final MonitoringTables tables;

    JdbcUsageEventStore(JdbcTemplate jdbc, MonitoringTables tables) {
        this.jdbc = jdbc;
        this.tables = tables;
    }

    /**
     * Inserta un lote. Los rotulos se recortan al ancho de la columna: un solo rotulo
     * de mas de 120 caracteres haria fallar el lote entero y perderia los demas.
     */
    void insertBatch(List<UsageEvent> events) {
        jdbc.batchUpdate(tables.insertEvent(), events, events.size(), (ps, e) -> {
            ps.setTimestamp(1, Timestamp.from(e.occurredAt()));
            ps.setString(2, e.type().name());
            ps.setString(3, label(e.originLabel()));
            ps.setString(4, label(e.destinationLabel()));
            if (e.routeId() == null) {
                ps.setNull(5, Types.BIGINT);
            } else {
                ps.setLong(5, e.routeId());
            }
            ps.setString(6, e.outcome().name());
            ps.setString(7, e.etaSource() == null ? null : e.etaSource().name());
            if (e.latencyMs() == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, e.latencyMs());
            }
            ps.setBoolean(9, e.congestion());
        });
    }

    private static String label(String value) {
        // Un rotulo es un barrio del catalogo, pero llega del usuario: se sanea igual.
        return LogSanitizer.sanitizeAndTruncate(value, MAX_LABEL);
    }

    @Override
    public List<UsageEvent> findRecent(Instant since, int limit) {
        List<Object> args = new ArrayList<>();
        if (since != null) {
            args.add(Timestamp.from(since));
        }
        args.add(limit);

        return jdbc.query(tables.selectEvents(since != null), (rs, i) -> {
            long routeId = rs.getLong("route_id");
            Long route = rs.wasNull() ? null : routeId;
            int latency = rs.getInt("latency_ms");
            Integer latencyMs = rs.wasNull() ? null : latency;
            String eta = rs.getString("eta_source");
            return new UsageEvent(
                    rs.getLong("id"),
                    rs.getTimestamp("ts").toInstant(),
                    UsageEventType.valueOf(rs.getString("event_type")),
                    rs.getString("origin_label"),
                    rs.getString("destination_label"),
                    route,
                    SearchOutcome.valueOf(rs.getString("outcome")),
                    eta == null ? null : EtaSource.valueOf(eta),
                    latencyMs,
                    rs.getBoolean("congestion"));
        }, args.toArray());
    }

    @Override
    public long countSearchesBetween(Instant fromInclusive, Instant toExclusive) {
        Long total = jdbc.queryForObject(tables.countSearches(), Long.class,
                Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
        return total == null ? 0 : total;
    }

    @Override
    public List<RouteSearchCount> countSearchesByRoute(Instant since) {
        return jdbc.query(tables.countSearchesByRoute(),
                (rs, i) -> new RouteSearchCount(rs.getLong("route_id"), rs.getLong("searches")),
                Timestamp.from(since));
    }

    /** Borra lo anterior a {@code cutoff}; devuelve cuantas filas. */
    int deleteOlderThan(Instant cutoff) {
        return jdbc.update(tables.deleteEventsOlderThan(), Timestamp.from(cutoff));
    }
}
