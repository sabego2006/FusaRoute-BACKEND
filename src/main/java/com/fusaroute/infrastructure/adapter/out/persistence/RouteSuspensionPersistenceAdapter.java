package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.exception.SuspensionNotFoundException;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.port.out.ActiveSuspensionsPort;
import com.fusaroute.domain.port.out.EffectiveSuspensionCountPort;
import com.fusaroute.domain.port.out.RouteSuspensionRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de salida de las suspensiones de ruta (RF-12). Implementa los dos puertos: el de
 * administracion y el del predicado "efectivamente suspendida" ({@link ActiveSuspensionsPort}), que es una
 * sola consulta ({@link SpringDataRouteSuspensionRepository#EFFECTIVE}).
 */
@Component
public class RouteSuspensionPersistenceAdapter implements RouteSuspensionRepositoryPort, ActiveSuspensionsPort,
        EffectiveSuspensionCountPort {

    private final SpringDataRouteSuspensionRepository repository;

    public RouteSuspensionPersistenceAdapter(SpringDataRouteSuspensionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RouteSuspension save(RouteSuspension suspension, Long createdBy) {
        RouteSuspensionJpaEntity saved = repository.saveAndFlush(new RouteSuspensionJpaEntity(
                suspension.routeId(), suspension.startsAt(), suspension.endsAt(), suspension.reason(), createdBy));
        return reload(saved.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RouteSuspension> findById(Long id) {
        return repository.findRowById(id).map(RouteSuspensionPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteSuspension> findAll() {
        return repository.findAllWithRoute().stream().map(RouteSuspensionPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteSuspension> findNotLiftedByRoute(Long routeId) {
        return repository.findNotLiftedByRoute(routeId).stream()
                .map(RouteSuspensionPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public RouteSuspension markLifted(Long id, Instant at) {
        repository.lift(id, at);
        return reload(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteSuspension> findEffectiveAt(Instant now) {
        return repository.findEffectiveAt(now).stream().map(RouteSuspensionPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countEffectiveAt(Instant now) {
        return repository.countEffectiveAt(now);
    }

    private RouteSuspension reload(Long id) {
        return repository.findRowById(id).map(RouteSuspensionPersistenceAdapter::toDomain)
                .orElseThrow(SuspensionNotFoundException::new);
    }

    static RouteSuspension toDomain(RouteSuspensionRow row) {
        return new RouteSuspension(row.getId(), row.getRouteId(), row.getRouteName(), row.getStartsAt(),
                row.getEndsAt(), row.getReason(), row.getLiftedAt());
    }
}
