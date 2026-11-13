package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adaptador de salida que implementa {@link RouteRepositoryPort} sobre Spring Data.
 *
 * {@code @Transactional(readOnly = true)} es necesario, no decorativo: con
 * {@code open-in-view=false} las colecciones LAZY solo se pueden leer dentro de la
 * transaccion, y el mapeo a dominio las recorre.
 *
 * <p><b>Suspension temporal (RF-12).</b> Toda lectura publica de rutas pasa por aqui, asi que aqui se aplica
 * el predicado "efectivamente suspendida": una ruta ACTIVA con una ventana vigente sale como
 * {@link RouteStatus#SUSPENDIDA}, y el filtro de {@code Route::isActive} que ya usan el catalogo, el detalle,
 * la tarifa, los lugares, la busqueda y "Ya me subi" la deja fuera sin tocar ninguno de esos casos de uso.
 * {@code routes.status} no cambia en la base: no hay scheduler, la ruta vuelve sola al cerrarse la ventana.
 * Una INACTIVA sigue INACTIVA, aunque tenga una ventana vigente.
 */
@Component
public class RoutePersistenceAdapter implements RouteRepositoryPort {

    private final SpringDataRouteRepository repository;
    private final SpringDataRouteSuspensionRepository suspensions;
    private final Clock clock;

    public RoutePersistenceAdapter(SpringDataRouteRepository repository,
                                   SpringDataRouteSuspensionRepository suspensions, Clock clock) {
        this.repository = repository;
        this.suspensions = suspensions;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Route> findAll() {
        Set<Long> suspended = suspendedNow();
        return repository.findAll().stream()
                .map(RoutePersistenceMapper::toDomain)
                .map(route -> presented(route, suspended))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Route> findById(Long id) {
        Set<Long> suspended = suspendedNow();
        return repository.findById(id)
                .map(RoutePersistenceMapper::toDomain)
                .map(route -> presented(route, suspended));
    }

    private Set<Long> suspendedNow() {
        return Set.copyOf(suspensions.findEffectiveRouteIdsAt(clock.instant()));
    }

    private static Route presented(Route route, Set<Long> suspended) {
        return route.status() == RouteStatus.ACTIVA && suspended.contains(route.id())
                ? route.withStatus(RouteStatus.SUSPENDIDA)
                : route;
    }
}
