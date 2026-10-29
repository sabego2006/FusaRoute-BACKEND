package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repositorio Spring Data sobre {@link RouteSignJpaEntity} (SCRUM-176). */
public interface SpringDataRouteSignRepository extends JpaRepository<RouteSignJpaEntity, Long> {

    List<RouteSignJpaEntity> findByRouteIdOrderByOrderIndex(Long routeId);
}
