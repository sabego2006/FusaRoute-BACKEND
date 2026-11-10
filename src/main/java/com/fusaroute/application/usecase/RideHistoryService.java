package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Ride;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.port.in.GetRideHistoryUseCase;
import com.fusaroute.domain.port.in.RecordRideCommand;
import com.fusaroute.domain.port.in.RecordRideUseCase;
import com.fusaroute.domain.port.out.RideRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;

import java.time.Clock;
import java.util.List;

public class RideHistoryService implements RecordRideUseCase, GetRideHistoryUseCase {

    private final RideRepositoryPort rides;
    private final RouteRepositoryPort routes;
    private final Clock clock;

    public RideHistoryService(RideRepositoryPort rides, RouteRepositoryPort routes, Clock clock) {
        this.rides = rides;
        this.routes = routes;
        this.clock = clock;
    }

    @Override
    public Ride record(RecordRideCommand command) {
        if (command.routeId() == null) {
            throw new RouteNotFoundException();
        }
        // Una ruta suspendida es, para quien se sube, una ruta que no existe: el
        // catalogo publico tampoco la muestra.
        Route route = routes.findById(command.routeId())
                .filter(Route::isActive)
                .orElseThrow(RouteNotFoundException::new);
        Ride ride = Ride.boarded(command.userId(), route, command.destinationLabel(),
                command.destinationPlaceId(), command.destinationLat(), command.destinationLng(),
                command.originLabel(), clock.instant());
        return rides.saveKeepingLatest(ride, Ride.MAX_PER_USER);
    }

    @Override
    public List<Ride> ridesOf(Long userId) {
        return rides.findRecentByUser(userId, Ride.MAX_PER_USER);
    }
}
