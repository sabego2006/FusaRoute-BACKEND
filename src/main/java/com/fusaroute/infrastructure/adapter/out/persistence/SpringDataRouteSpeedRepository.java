package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Repositorio Spring Data de las velocidades observadas por ruta (V22). */
public interface SpringDataRouteSpeedRepository extends JpaRepository<RouteSpeedObservationJpaEntity, Long> {

    /** Las observaciones mas recientes de todas las rutas; el recorte por ruta lo hace el adaptador. */
    @Query("select o from RouteSpeedObservationJpaEntity o order by o.observedAt desc")
    List<RouteSpeedObservationJpaEntity> findMostRecent(Pageable page);
}
