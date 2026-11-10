package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Ride;
import com.fusaroute.domain.port.out.RideRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RidePersistenceAdapter implements RideRepositoryPort {

    private final SpringDataRideHistoryRepository repository;

    public RidePersistenceAdapter(SpringDataRideHistoryRepository repository) {
        this.repository = repository;
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

    @Override
    @Transactional(readOnly = true)
    public List<Ride> findRecentByUser(Long userId, int limit) {
        return repository.findRecentByUser(userId, limit).stream()
                .map(RidePersistenceAdapter::toDomain).toList();
    }

    static Ride toDomain(RideHistoryRow row) {
        return new Ride(row.getId(), row.getUserId(), row.getRouteId(), row.getRouteName(), row.getSignFile(),
                row.getDestinationLabel(), row.getDestinationPlaceId(),
                new Coordinate(row.getDestinationLat(), row.getDestinationLng()),
                row.getOriginLabel(), row.getBoardedAt());
    }
}
