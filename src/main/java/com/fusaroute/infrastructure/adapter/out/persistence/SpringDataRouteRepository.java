package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Repositorio Spring Data sobre {@link RouteJpaEntity}; el CRUD basico basta para el catalogo. */
public interface SpringDataRouteRepository extends JpaRepository<RouteJpaEntity, Long> {

    /**
     * Id y nombre de las rutas pedidas, sin filtrar por estado: el ranking del panel de metricas (SCRUM-26)
     * es historico y muestra tambien las INACTIVAS. No toca trazado, barrios ni tarifas.
     */
    @Query(value = "SELECT r.id AS id, r.name AS name FROM routes r WHERE r.id IN (:ids)", nativeQuery = true)
    List<RouteNameRow> findNamesByIds(@Param("ids") Collection<Long> ids);
}
