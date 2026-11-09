package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de salida de {@link PlaceRepositoryPort}. {@code readOnly} es necesario: con
 * {@code open-in-view=false} la coleccion LAZY de alias solo se lee dentro de la transaccion.
 *
 * Los lugares cambian solo por migracion y se leen en cada busqueda, asi que se guardan en memoria
 * durante {@link #TTL_MILLIS}. Tras desplegar una migracion nueva la instancia es nueva y arranca vacia.
 */
@Component
public class PlacePersistenceAdapter implements PlaceRepositoryPort {

    private static final long TTL_MILLIS = 10 * 60 * 1000L;

    private final SpringDataPlaceRepository repository;

    private volatile List<Place> cached;
    private volatile long cachedAt;

    public PlacePersistenceAdapter(SpringDataPlaceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Place> findAll() {
        List<Place> snapshot = cached;
        long now = System.currentTimeMillis();
        if (snapshot != null && now - cachedAt < TTL_MILLIS) {
            return snapshot;
        }
        List<Place> fresh = repository.findAll().stream().map(PlacePersistenceAdapter::toDomain).toList();
        cached = fresh;
        cachedAt = now;
        return fresh;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Place> findById(Long id) {
        return repository.findById(id).map(PlacePersistenceAdapter::toDomain);
    }

    private static Place toDomain(PlaceJpaEntity entity) {
        List<String> aliases = entity.getAliases() == null ? List.of() : entity.getAliases();
        return new Place(entity.getId(), entity.getSlug(), entity.getName(),
                new Coordinate(entity.getLat(), entity.getLng()), aliases);
    }
}
