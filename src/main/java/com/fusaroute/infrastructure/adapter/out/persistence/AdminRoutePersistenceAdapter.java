package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusaroute.domain.exception.RouteConflictException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.FareChange;
import com.fusaroute.domain.model.RouteDraft;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.out.AdminRouteRepositoryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Escritura y lectura de rutas para el administrador (RF-11) con {@link JdbcTemplate}.
 * Se hace con JDBC y no con {@link RouteJpaEntity} porque esa entidad es de solo lectura
 * a proposito (no mapea {@code updated_at} y sus tarifas no son insertables) y el
 * catalogo publico depende de que siga asi.
 *
 * <p>Comparte el pool de Hikari y la transaccion de JPA: un error a mitad de la escritura
 * revierte la ruta, sus barrios y sus tarifas juntos.
 */
@Component
class AdminRoutePersistenceAdapter implements AdminRouteRepositoryPort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String NEIGHBORHOODS_SELECT =
            "SELECT route_id, neighborhood_name FROM route_neighborhoods";
    private static final String NEIGHBORHOODS_ORDER = " ORDER BY route_id, order_index";
    private static final String NEIGHBORHOODS_ALL = NEIGHBORHOODS_SELECT + NEIGHBORHOODS_ORDER;
    private static final String NEIGHBORHOODS_ONE = NEIGHBORHOODS_SELECT + " WHERE route_id = ?" + NEIGHBORHOODS_ORDER;
    private static final String FARES_SELECT =
            "SELECT route_id, amount, reference_point, order_index, valid_from FROM fares";
    private static final String FARES_ALL = FARES_SELECT;
    private static final String FARES_ONE = FARES_SELECT + " WHERE route_id = ?";
    private static final String ROUTES_SELECT =
            "SELECT id, name, route_type, status, sign_file, path_geojson::text AS path, updated_at FROM routes";
    private static final String ROUTES_ALL = ROUTES_SELECT + " ORDER BY name";
    private static final String ROUTES_ONE = ROUTES_SELECT + " WHERE id = ? ORDER BY name";

    private final JdbcTemplate jdbc;

    AdminRoutePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminRoute> findAll() {
        return load(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminRoute> findById(Long id) {
        return load(id).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name, Long excludeId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM routes WHERE lower(name) = lower(?) AND id <> ?",
                Integer.class, name, excludeId == null ? -1L : excludeId);
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySignFile(String signFile, Long excludeId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM routes WHERE sign_file = ? AND id <> ?",
                Integer.class, signFile, excludeId == null ? -1L : excludeId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public AdminRoute create(RouteDraft draft, List<Fare> fares) {
        Long id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO routes (name, route_type, status, path_geojson, sign_file)
                    VALUES (?, ?, 'ACTIVA', ?::jsonb, ?) RETURNING id
                    """, Long.class, draft.name(), draft.type().name(), pathJson(draft), draft.signFile());
        } catch (DuplicateKeyException e) {
            throw new RouteConflictException("Ya existe una ruta con ese nombre o letrero");
        }
        insertNeighborhoods(id, draft.neighborhoods());
        fares.forEach(fare -> upsertFare(id, fare));
        return findById(id).orElseThrow(RouteNotFoundException::new);
    }

    @Override
    @Transactional
    public AdminRoute update(Long id, RouteDraft draft, FareChange fareChange) {
        try {
            jdbc.update("""
                    UPDATE routes SET name = ?, route_type = ?, path_geojson = ?::jsonb, sign_file = ?,
                                      updated_at = now()
                     WHERE id = ?
                    """, draft.name(), draft.type().name(), pathJson(draft), draft.signFile(), id);
        } catch (DuplicateKeyException e) {
            throw new RouteConflictException("Ya existe una ruta con ese nombre o letrero");
        }
        jdbc.update("DELETE FROM route_neighborhoods WHERE route_id = ?", id);
        insertNeighborhoods(id, draft.neighborhoods());
        if (fareChange.changed()) {
            for (String point : fareChange.removedPoints()) {
                jdbc.update("DELETE FROM fares WHERE route_id = ? AND reference_point IS NOT DISTINCT FROM ?",
                        id, point);
            }
            fareChange.toWrite().forEach(fare -> upsertFare(id, fare));
        }
        return findById(id).orElseThrow(RouteNotFoundException::new);
    }

    @Override
    @Transactional
    public void setStatus(Long id, RouteStatus status) {
        jdbc.update("UPDATE routes SET status = ?, updated_at = now() WHERE id = ?", status.name(), id);
    }

    /** Si ya hay una fila de hoy para ese punto, la restriccion unica obliga a actualizarla en vez de insertar. */
    private void upsertFare(Long routeId, Fare fare) {
        jdbc.update("""
                INSERT INTO fares (route_id, amount, reference_point, order_index, valid_from)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT ON CONSTRAINT uq_fares_route_point
                DO UPDATE SET amount = EXCLUDED.amount, order_index = EXCLUDED.order_index
                """, routeId, fare.amount(), fare.referencePoint(), fare.orderIndex(), fare.validFrom());
    }

    private void insertNeighborhoods(Long routeId, List<String> neighborhoods) {
        for (int i = 0; i < neighborhoods.size(); i++) {
            jdbc.update("INSERT INTO route_neighborhoods (route_id, order_index, neighborhood_name) VALUES (?, ?, ?)",
                    routeId, i, neighborhoods.get(i).strip());
        }
    }

    private static String pathJson(RouteDraft draft) {
        if (!draft.hasPath()) {
            return null;
        }
        // LinkedHashMap y no Map.of: el orden "type" primero es estable y legible en la base.
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "LineString");
        geometry.put("coordinates", draft.pathPositions());
        try {
            return JSON.writeValueAsString(geometry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el trazado", e);
        }
    }

    /** Rutas (una, o todas si {@code id} es nulo) con barrios y todas sus filas de tarifa. SQL fijo, sin concatenar. */
    private List<AdminRoute> load(Long id) {
        Object[] args = id == null ? new Object[0] : new Object[] {id};

        Map<Long, List<String>> neighborhoods = new HashMap<>();
        jdbc.query(id == null ? NEIGHBORHOODS_ALL : NEIGHBORHOODS_ONE,
                rs -> {
                    neighborhoods.computeIfAbsent(rs.getLong("route_id"), k -> new ArrayList<>())
                            .add(rs.getString("neighborhood_name"));
                }, args);

        Map<Long, List<Fare>> fares = new HashMap<>();
        jdbc.query(id == null ? FARES_ALL : FARES_ONE,
                rs -> {
                    fares.computeIfAbsent(rs.getLong("route_id"), k -> new ArrayList<>())
                            .add(new Fare(rs.getString("reference_point"), rs.getBigDecimal("amount"),
                                    rs.getInt("order_index"), rs.getDate("valid_from").toLocalDate()));
                }, args);

        return jdbc.query(id == null ? ROUTES_ALL : ROUTES_ONE,
                (rs, i) -> new AdminRoute(rs.getLong("id"), rs.getString("name"),
                        RouteType.valueOf(rs.getString("route_type")), RouteStatus.valueOf(rs.getString("status")),
                        rs.getString("sign_file"), rs.getString("path"),
                        neighborhoods.getOrDefault(rs.getLong("id"), List.of()),
                        fares.getOrDefault(rs.getLong("id"), List.of()),
                        rs.getTimestamp("updated_at").toInstant()),
                args);
    }
}
