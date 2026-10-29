package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataSearchHistoryRepository extends JpaRepository<SearchHistoryJpaEntity, Long> {

    /**
     * El {@code Pageable} acota cuantas filas se traen. Ese orden
     * ({@code user_id}, {@code created_at DESC}) es exactamente el del indice
     * {@code ix_search_history_user_created}, asi que Postgres lee las filas sin
     * ordenar nada despues.
     */
    List<SearchHistoryJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Borra lo que sobra del historial de un usuario.
     *
     * La subconsulta elige los ids a conservar en vez de calcular una fecha de
     * corte: si dos busquedas caen en el mismo instante, un corte por fecha
     * borraria las dos o ninguna, y el tope quedaria mal.
     */
    @Modifying
    @Query(value = """
            DELETE FROM search_history
             WHERE user_id = :userId
               AND id NOT IN (
                   SELECT id FROM search_history
                    WHERE user_id = :userId
                    ORDER BY created_at DESC, id DESC
                    LIMIT :keep)
            """, nativeQuery = true)
    int deleteOlderBeyond(@Param("userId") Long userId, @Param("keep") int keep);
}
