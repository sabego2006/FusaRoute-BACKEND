package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.port.out.TrafficSectorRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Adaptador de salida que implementa {@link TrafficSectorRepositoryPort} sobre Spring Data.
 *
 * {@code @Transactional(readOnly = true)} es necesario, no decorativo: con
 * {@code open-in-view=false} la coleccion LAZY de barrios solo se puede leer dentro de
 * la transaccion, y el mapeo a dominio la recorre.
 */
@Component
public class TrafficSectorPersistenceAdapter implements TrafficSectorRepositoryPort {

    private final SpringDataTrafficSectorRepository repository;

    public TrafficSectorPersistenceAdapter(SpringDataTrafficSectorRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrafficSector> findAll() {
        return repository.findAll().stream().map(TrafficSectorPersistenceAdapter::toDomain).toList();
    }

    private static TrafficSector toDomain(TrafficSectorJpaEntity entity) {
        Set<String> neighborhoods = entity.getNeighborhoods() == null ? Set.of() : entity.getNeighborhoods();
        return new TrafficSector(entity.getId(), entity.getName(), neighborhoods,
                entity.getStartTime(), entity.getEndTime());
    }
}
