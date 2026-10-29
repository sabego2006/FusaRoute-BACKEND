package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.RouteSign;
import com.fusaroute.domain.port.in.GetRouteSignsUseCase;
import com.fusaroute.domain.port.out.RouteSignRepositoryPort;

import java.util.List;

/**
 * Entrega los letreros de una ruta tal como los dejo el repositorio (SCRUM-176). El
 * orden y el contenido ya quedan resueltos en la persistencia (ver
 * {@code RouteSignPersistenceMapper}); aqui no hay regla de negocio que aplicar — a
 * diferencia de {@link GetCongestionWarningService}, un letrero no depende de la hora
 * ni de que tramo se este consultando.
 */
public class GetRouteSignsService implements GetRouteSignsUseCase {

    private final RouteSignRepositoryPort routeSignRepository;

    public GetRouteSignsService(RouteSignRepositoryPort routeSignRepository) {
        this.routeSignRepository = routeSignRepository;
    }

    @Override
    public List<RouteSign> forRoute(Long routeId) {
        return routeSignRepository.findByRouteId(routeId);
    }
}
