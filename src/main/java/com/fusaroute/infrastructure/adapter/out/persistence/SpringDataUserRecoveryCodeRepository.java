package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repositorio Spring Data para {@link UserRecoveryCodeJpaEntity}. La consulta
 * de activos filtra en SQL para no cargar los ya consumidos y el reemplazo
 * borra todos los del usuario en una sentencia.
 */
public interface SpringDataUserRecoveryCodeRepository
        extends JpaRepository<UserRecoveryCodeJpaEntity, Long> {

    List<UserRecoveryCodeJpaEntity> findByUserIdAndUsedFalse(Long userId);

    @Modifying
    @Transactional
    @Query("delete from UserRecoveryCodeJpaEntity c where c.userId = :userId")
    void deleteAllByUserId(Long userId);
}
