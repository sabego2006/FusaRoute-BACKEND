package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de los sectores con congestion (RF-09). Solo lectura. */
public interface SpringDataTrafficSectorRepository extends JpaRepository<TrafficSectorJpaEntity, Long> {
}
