package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data sobre {@link RouteJpaEntity}; el CRUD basico basta para el catalogo. */
public interface SpringDataRouteRepository extends JpaRepository<RouteJpaEntity, Long> {
}
