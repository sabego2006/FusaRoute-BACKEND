package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Ride;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import com.fusaroute.domain.port.in.GetRideHistoryUseCase;
import com.fusaroute.domain.port.in.RecordRideCommand;
import com.fusaroute.domain.port.in.RecordRideUseCase;
import com.fusaroute.domain.port.out.RideRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.UsageEventRecorderPort;

import java.time.Clock;
import java.util.List;

public class RideHistoryService implements RecordRideUseCase, GetRideHistoryUseCase {

    private final RideRepositoryPort rides;
    private final RouteRepositoryPort routes;
    private final Clock clock;
    private final UsageEventRecorderPort usageEvents;

    /** Sin registro de eventos de uso: para pruebas que no miden el uso. */
    public RideHistoryService(RideRepositoryPort rides, RouteRepositoryPort routes, Clock clock) {
        this(rides, routes, clock, event -> { });
    }

    public RideHistoryService(RideRepositoryPort rides, RouteRepositoryPort routes, Clock clock,
                              UsageEventRecorderPort usageEvents) {
        this.rides = rides;
        this.routes = routes;
        this.clock = clock;
        this.usageEvents = usageEvents;
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
        Ride saved = rides.saveKeepingLatest(ride, Ride.MAX_PER_USER);
        recordBoarded(ride, route);
        return saved;
    }

    /**
     * Evento BOARDED en search_events (SCRUM-186): solo la ruta y las etiquetas, nunca el usuario
     * ni coordenadas. Asincrono; si falla, el viaje ya quedo guardado y no se toca.
     */
    private void recordBoarded(Ride ride, Route route) {
        try {
            usageEvents.record(UsageEvent.of(clock.instant(), UsageEventType.BOARDED,
                    ride.originLabel(), ride.destinationLabel(), route.id(),
                    SearchOutcome.FOUND, null, null, false));
        } catch (RuntimeException ignored) {
            // Una estadistica nunca tumba el guardado del viaje.
        }
    }

    @Override
    public List<Ride> ridesOf(Long userId) {
        return rides.findRecentByUser(userId, Ride.MAX_PER_USER);
    }
}
