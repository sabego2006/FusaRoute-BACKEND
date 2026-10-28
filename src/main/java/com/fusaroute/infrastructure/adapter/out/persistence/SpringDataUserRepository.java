package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio Spring Data sobre {@link UserJpaEntity}. Spring implementa el CRUD
 * y la derivacion de {@code existsByEmail} y {@code findByEmail} a partir del
 * nombre del metodo.
 */
public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, Long> {

    boolean existsByEmail(String email);

    Optional<UserJpaEntity> findByEmail(String email);
}
