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
 *
 * <p>Los sectores cambian solo por migracion (no hay escritura desde la aplicacion) y se leen en cada
 * detalle de ruta y en cada busqueda, asi que se guardan en memoria durante {@link #TTL_MILLIS}
 * (SCRUM-192), igual que los lugares. Tras desplegar una migracion nueva la instancia es nueva y arranca
 * vacia. Lo que depende de la hora (si el aviso esta encendido) lo decide el dominio en cada lectura.
 */
@Component
public class TrafficSectorPersistenceAdapter implements TrafficSectorRepositoryPort {

    private static final long TTL_MILLIS = 10 * 60 * 1000L;

    private final SpringDataTrafficSectorRepository repository;

    private volatile List<TrafficSector> cached;
    private volatile long cachedAt;

    public TrafficSectorPersistenceAdapter(SpringDataTrafficSectorRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrafficSector> findAll() {
        List<TrafficSector> snapshot = cached;
        long now = System.currentTimeMillis();
        if (snapshot != null && now - cachedAt < TTL_MILLIS) {
            return snapshot;
        }
        List<TrafficSector> fresh = repository.findAll().stream().map(TrafficSectorPersistenceAdapter::toDomain).toList();
        cached = fresh;
        cachedAt = now;
        return fresh;
    }

    private static TrafficSector toDomain(TrafficSectorJpaEntity entity) {
        Set<String> neighborhoods = entity.getNeighborhoods() == null ? Set.of() : entity.getNeighborhoods();
        return new TrafficSector(entity.getId(), entity.getName(), neighborhoods,
                entity.getStartTime(), entity.getEndTime(),
                entity.getCenterLat(), entity.getCenterLng(), entity.getRadiusM());
    }
}
