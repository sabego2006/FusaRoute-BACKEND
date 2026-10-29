package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.RouteSign;
import com.fusaroute.domain.port.out.RouteSignRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adaptador de salida que implementa {@link RouteSignRepositoryPort} sobre Spring Data
 * (SCRUM-176).
 *
 * {@code @Transactional(readOnly = true)} es necesario: con {@code open-in-view=false}
 * las placas y lineas LAZY solo se pueden leer dentro de la transaccion, y el mapeo a
 * dominio las recorre.
 */
@Component
public class RouteSignPersistenceAdapter implements RouteSignRepositoryPort {

    private final SpringDataRouteSignRepository repository;

    public RouteSignPersistenceAdapter(SpringDataRouteSignRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteSign> findByRouteId(Long routeId) {
        return repository.findByRouteIdOrderByOrderIndex(routeId).stream()
                .map(RouteSignPersistenceMapper::toDomain)
                .toList();
    }
}
