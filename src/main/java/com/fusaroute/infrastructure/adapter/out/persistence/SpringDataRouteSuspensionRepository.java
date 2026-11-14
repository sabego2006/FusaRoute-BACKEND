package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consultas de las suspensiones de ruta (RF-12). {@link #EFFECTIVE} es EL predicado "efectivamente
 * suspendida": la lectura publica de rutas, la busqueda y los avisos pasan todos por el. Un cambio a la
 * regla se hace aqui y solo aqui.
 */
public interface SpringDataRouteSuspensionRepository extends JpaRepository<RouteSuspensionJpaEntity, Long> {

    String SELECT = """
            SELECT s.id AS id, s.route_id AS routeId, r.name AS routeName, s.starts_at AS startsAt,
                   s.ends_at AS endsAt, s.reason AS reason, s.lifted_at AS liftedAt
              FROM route_suspensions s
              JOIN routes r ON r.id = s.route_id
            """;

    /** starts_at <= ahora < ends_at y sin levantar: ventana semiabierta. */
    String EFFECTIVE = " s.lifted_at IS NULL AND s.starts_at <= :now AND :now < s.ends_at ";

    @Query(value = SELECT + " ORDER BY s.id", nativeQuery = true)
    List<RouteSuspensionRow> findAllWithRoute();

    @Query(value = SELECT + " WHERE s.id = :id", nativeQuery = true)
    Optional<RouteSuspensionRow> findRowById(@Param("id") Long id);

    @Query(value = SELECT + " WHERE s.route_id = :routeId AND s.lifted_at IS NULL ORDER BY s.starts_at, s.id",
            nativeQuery = true)
    List<RouteSuspensionRow> findNotLiftedByRoute(@Param("routeId") Long routeId);

    @Query(value = SELECT + " WHERE" + EFFECTIVE + "ORDER BY s.ends_at, s.id", nativeQuery = true)
    List<RouteSuspensionRow> findEffectiveAt(@Param("now") Instant now);

    /** Solo los ids: es lo que necesita la lectura publica de rutas, que corre en cada peticion. */
    @Query(value = "SELECT DISTINCT s.route_id FROM route_suspensions s WHERE" + EFFECTIVE, nativeQuery = true)
    List<Long> findEffectiveRouteIdsAt(@Param("now") Instant now);

    /** Metrica 5 del panel de RF-13 (SCRUM-26): cuenta con el mismo predicado {@link #EFFECTIVE}. */
    @Query(value = "SELECT count(*) FROM route_suspensions s WHERE" + EFFECTIVE, nativeQuery = true)
    long countEffectiveAt(@Param("now") Instant now);

    /** El {@code lifted_at IS NULL} evita pisar la fecha de una suspension que otro ya levanto. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE route_suspensions SET lifted_at = :at WHERE id = :id AND lifted_at IS NULL",
            nativeQuery = true)
    int lift(@Param("id") Long id, @Param("at") Instant at);
}
