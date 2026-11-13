package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Ride;
import com.fusaroute.domain.port.out.RideRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;

@Component
public class RidePersistenceAdapter implements RideRepositoryPort {

    private final SpringDataRideHistoryRepository repository;
    private final SpringDataRouteSuspensionRepository suspensions;
    private final Clock clock;

    public RidePersistenceAdapter(SpringDataRideHistoryRepository repository,
                                  SpringDataRouteSuspensionRepository suspensions, Clock clock) {
        this.repository = repository;
        this.suspensions = suspensions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Ride saveKeepingLatest(Ride ride, int keep) {
        // saveAndFlush: el DELETE nativo tiene que ver el INSERT. Ambos van en la
        // misma transaccion: si el recorte falla, se revierte tambien el guardado.
        RideHistoryJpaEntity saved = repository.saveAndFlush(new RideHistoryJpaEntity(
                ride.userId(), ride.routeId(), ride.destinationLabel(), ride.destinationPlaceId(),
                ride.destination().latitude(), ride.destination().longitude(),
                ride.originLabel(), ride.boardedAt()));
        repository.deleteOlderBeyond(ride.userId(), keep);
        return new Ride(saved.getId(), ride.userId(), ride.routeId(), ride.routeName(), ride.signFile(),
                ride.destinationLabel(), ride.destinationPlaceId(), ride.destination(),
                ride.originLabel(), ride.boardedAt());
    }

    /**
     * El historial lee {@code routes.status} directo, asi que tambien aplica el predicado de suspension
     * (RF-12): un viaje de una ruta suspendida hoy sale como "ruta no disponible" hasta que vuelva.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Ride> findRecentByUser(Long userId, int limit) {
        Set<Long> suspended = Set.copyOf(suspensions.findEffectiveRouteIdsAt(clock.instant()));
        return repository.findRecentByUser(userId, limit).stream()
                .map(row -> toDomain(row, suspended)).toList();
    }

    static Ride toDomain(RideHistoryRow row) {
        return toDomain(row, Set.of());
    }

    static Ride toDomain(RideHistoryRow row, Set<Long> suspendedRouteIds) {
        boolean available = !"INACTIVA".equals(row.getRouteStatus()) && !suspendedRouteIds.contains(row.getRouteId());
        return new Ride(row.getId(), row.getUserId(), row.getRouteId(), row.getRouteName(), row.getSignFile(),
                row.getDestinationLabel(), row.getDestinationPlaceId(),
                new Coordinate(row.getDestinationLat(), row.getDestinationLng()),
                row.getOriginLabel(), row.getBoardedAt(), available);
    }
}
