package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * El id de la entidad es el id del usuario, asi que los metodos heredados
 * ({@code findById}, {@code save}, {@code deleteById}) ya cubren todo lo que el puerto
 * necesita: no hay una sola consulta que declarar.
 */
public interface SpringDataFavoriteDestinationRepository
        extends JpaRepository<FavoriteDestinationJpaEntity, Long> {
}
