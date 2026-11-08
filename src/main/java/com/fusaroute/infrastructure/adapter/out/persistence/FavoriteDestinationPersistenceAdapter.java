package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.FavoriteDestination;
import com.fusaroute.domain.port.out.FavoriteDestinationRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adaptador de salida de {@link FavoriteDestinationRepositoryPort} sobre Spring Data
 * (RF-10, SCRUM-21).
 */
@Component
public class FavoriteDestinationPersistenceAdapter implements FavoriteDestinationRepositoryPort {

    private final SpringDataFavoriteDestinationRepository repository;

    public FavoriteDestinationPersistenceAdapter(SpringDataFavoriteDestinationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public FavoriteDestination save(FavoriteDestination favorite) {
        FavoriteDestinationJpaEntity saved = repository.save(new FavoriteDestinationJpaEntity(
                favorite.userId(), favorite.destination(), favorite.savedAt()));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FavoriteDestination> findByUser(Long userId) {
        return repository.findById(userId).map(FavoriteDestinationPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void deleteByUser(Long userId) {
        // deleteById no falla si la fila no existe, que es justo lo que el puerto
        // promete: quitar un favorito que no habia no es un error.
        repository.deleteById(userId);
    }

    private static FavoriteDestination toDomain(FavoriteDestinationJpaEntity entity) {
        return new FavoriteDestination(entity.getUserId(), entity.getDestination(), entity.getCreatedAt());
    }
}
