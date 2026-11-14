package com.fusaroute.infrastructure.adapter.out.monitoring;

import java.util.regex.Pattern;

/**
 * Todo el SQL de monitoreo (SCRUM-186), con las tablas calificadas con el esquema.
 *
 * <p>El SQL de este paquete es JDBC puro, no JPA, y JDBC no recibe el
 * {@code hibernate.default_schema}. Sin calificar, funcionaria en DEV solo porque el rol
 * trae un {@code search_path} propio, y se romperia en silencio si alguien cambia esa
 * propiedad en el servidor. El CLAUDE.md del repo pide que el esquema se declare, no
 * que se herede del rol; por eso las tablas se califican con el mismo {@code DB_SCHEMA}
 * que usan Flyway e Hibernate.
 *
 * <p>Un identificador no se puede pasar como parametro de JDBC, asi que el esquema entra
 * al texto del SQL. Para que eso ocurra en UN solo lugar y no en cada consulta, aqui se
 * valida contra un identificador simple y se construyen, una vez, las sentencias
 * completas; los almacenes solo las ejecutan. Los filtros opcionales ({@code level},
 * {@code since}) no concatenan nada: cada combinacion es una sentencia fija con sus
 * {@code ?}.
 */
public final class MonitoringTables {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final String APP_LOGS_COLUMNS = "id, ts, level, logger, message, exception, thread, profile";
    private static final String EVENTS_COLUMNS = "id, ts, event_type, origin_label, destination_label, route_id,"
            + " outcome, eta_source, latency_ms, congestion";

    private final String schema;
    private final String insertAppLog;
    private final String deleteAppLogs;
    private final String[] selectAppLogs;
    private final String insertEvent;
    private final String deleteEvents;
    private final String[] selectEvents;
    private final String countSearches;
    private final String countSearchesByRoute;

    public MonitoringTables(String schema) {
        if (schema == null || !IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Nombre de esquema no valido: " + schema);
        }
        this.schema = schema;
        String appLogs = schema + ".app_logs";
        String events = schema + ".search_events";

        this.insertAppLog = "INSERT INTO " + appLogs
                + " (ts, level, logger, message, exception, thread, profile) VALUES (?, ?, ?, ?, ?, ?, ?)";
        this.deleteAppLogs = "DELETE FROM " + appLogs + " WHERE ts < ?";
        // Indice: bit 0 = filtra por level, bit 1 = filtra por since.
        this.selectAppLogs = new String[4];
        for (int variant = 0; variant < 4; variant++) {
            selectAppLogs[variant] = select(APP_LOGS_COLUMNS, appLogs, (variant & 1) != 0, (variant & 2) != 0);
        }

        this.insertEvent = "INSERT INTO " + events
                + " (ts, event_type, origin_label, destination_label, route_id, outcome,"
                + " eta_source, latency_ms, congestion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        this.deleteEvents = "DELETE FROM " + events + " WHERE ts < ?";
        // Indice: bit 0 = filtra por since.
        this.selectEvents = new String[2];
        for (int variant = 0; variant < 2; variant++) {
            selectEvents[variant] = select(EVENTS_COLUMNS, events, false, (variant & 1) != 0);
        }

        // Panel de RF-13 (SCRUM-26): agregados en la base, sin traer filas. event_type va como literal fijo.
        this.countSearches = "SELECT count(*) FROM " + events
                + " WHERE event_type = 'SEARCH' AND ts >= ? AND ts < ?";
        this.countSearchesByRoute = "SELECT route_id, count(*) AS searches FROM " + events
                + " WHERE event_type = 'SEARCH' AND route_id IS NOT NULL AND ts >= ? GROUP BY route_id";
    }

    private static String select(String columns, String table, boolean byLevel, boolean bySince) {
        StringBuilder sql = new StringBuilder("SELECT ").append(columns).append(" FROM ").append(table);
        String glue = " WHERE ";
        if (byLevel) {
            sql.append(glue).append("level = ?");
            glue = " AND ";
        }
        if (bySince) {
            sql.append(glue).append("ts >= ?");
        }
        // id DESC desempata filas con el mismo ts, que un lote escribe a menudo.
        return sql.append(" ORDER BY ts DESC, id DESC LIMIT ?").toString();
    }

    public String schema() {
        return schema;
    }

    String insertAppLog() {
        return insertAppLog;
    }

    String deleteAppLogsOlderThan() {
        return deleteAppLogs;
    }

    String selectAppLogs(boolean byLevel, boolean bySince) {
        return selectAppLogs[(byLevel ? 1 : 0) | (bySince ? 2 : 0)];
    }

    String insertEvent() {
        return insertEvent;
    }

    String deleteEventsOlderThan() {
        return deleteEvents;
    }

    String selectEvents(boolean bySince) {
        return selectEvents[bySince ? 1 : 0];
    }

    String countSearches() {
        return countSearches;
    }

    String countSearchesByRoute() {
        return countSearchesByRoute;
    }
}
