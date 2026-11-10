package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de los lugares canonicos (V21). Solo lectura. */
public interface SpringDataPlaceRepository extends JpaRepository<PlaceJpaEntity, Long> {
}
