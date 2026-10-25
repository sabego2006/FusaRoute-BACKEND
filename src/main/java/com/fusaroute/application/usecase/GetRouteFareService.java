package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.out.RouteRepositoryPort;

import java.time.Clock;
import java.time.LocalDate;

/** Costo del pasaje de una ruta (RF-06). Una ruta suspendida se comporta como inexistente. */
public class GetRouteFareService implements GetRouteFareUseCase {

    private final RouteRepositoryPort routeRepository;
    private final Clock clock;

    public GetRouteFareService(RouteRepositoryPort routeRepository, Clock clock) {
        this.routeRepository = routeRepository;
        this.clock = clock;
    }

    @Override
    public RouteFare getByRouteId(Long id) {
        Route route = routeRepository.findById(id)
                .filter(Route::isActive)
                .map(r -> r.withCurrentFares(LocalDate.now(clock)))
                .orElseThrow(RouteNotFoundException::new);
        return new RouteFare(route.id(), route.name(), route.type(), route.fares(),
                route.lastFareUpdate().orElse(null));
    }
}
