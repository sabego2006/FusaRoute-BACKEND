package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de salida que implementa {@link RouteRepositoryPort} sobre Spring Data.
 *
 * {@code @Transactional(readOnly = true)} es necesario, no decorativo: con
 * {@code open-in-view=false} las colecciones LAZY solo se pueden leer dentro de la
 * transaccion, y el mapeo a dominio las recorre.
 */
@Component
public class RoutePersistenceAdapter implements RouteRepositoryPort {

    private final SpringDataRouteRepository repository;

    public RoutePersistenceAdapter(SpringDataRouteRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Route> findAll() {
        return repository.findAll().stream().map(RoutePersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Route> findById(Long id) {
        return repository.findById(id).map(RoutePersistenceMapper::toDomain);
    }
}
