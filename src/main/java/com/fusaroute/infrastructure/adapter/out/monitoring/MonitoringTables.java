package com.fusaroute.infrastructure.adapter.out.monitoring;

import java.util.regex.Pattern;

/**
 * Nombres de tabla calificados con el esquema (SCRUM-186).
 *
 * <p>El SQL de este paquete es JDBC puro, no JPA, y JDBC no recibe el
 * {@code hibernate.default_schema}. Sin calificar, funcionaria en DEV solo porque el rol
 * trae un {@code search_path} propio, y se romperia en silencio si alguien cambia esa
 * propiedad en el servidor. El CLAUDE.md del repo pide que el esquema se declare, no
 * que se herede del rol; por eso las tablas se califican con el mismo {@code DB_SCHEMA}
 * que usan Flyway e Hibernate.
 *
 * <p>El esquema se valida contra un identificador simple porque se concatena en el SQL
 * (un identificador no se puede pasar como parametro).
 */
public record MonitoringTables(String schema) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public MonitoringTables {
        if (schema == null || !IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Nombre de esquema no valido: " + schema);
        }
    }

    String appLogs() {
        return schema + ".app_logs";
    }

    String searchEvents() {
        return schema + ".search_events";
    }
}
