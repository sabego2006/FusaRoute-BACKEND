package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.out.RouteRepositoryPort;

import java.time.Clock;
import java.time.LocalDate;

/** Detalle publico de una ruta. Una ruta suspendida se comporta como inexistente. */
public class GetRouteDetailService implements GetRouteDetailUseCase {

    private final RouteRepositoryPort routeRepository;
    private final Clock clock;

    public GetRouteDetailService(RouteRepositoryPort routeRepository, Clock clock) {
        this.routeRepository = routeRepository;
        this.clock = clock;
    }

    @Override
    public Route getById(Long id) {
        return routeRepository.findById(id)
                .filter(Route::isActive)
                .map(route -> route.withCurrentFares(LocalDate.now(clock)))
                .orElseThrow(RouteNotFoundException::new);
    }
}
