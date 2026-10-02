package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.TravelTimePort;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implementación del caso de uso de búsqueda de ruta.
 * Orquesta el filtrado de rutas candidatas y la selección de la mejor basada en tiempo.
 */
public class SearchRouteService implements SearchRouteUseCase {

    private final RouteRepositoryPort routeRepository;
    private final TravelTimePort travelTimePort;

    public SearchRouteService(RouteRepositoryPort routeRepository, TravelTimePort travelTimePort) {
        this.routeRepository = routeRepository;
        this.travelTimePort = travelTimePort;
    }

    @Override
    public Route findBestRoute(String originNeighborhood, Coordinate originCoord,
                               String destNeighborhood, Coordinate destCoord) {

        // 1. Obtener todas las rutas activas
        List<Route> activeRoutes = routeRepository.findAll().stream()
                .filter(Route::isActive)
                .toList();

        // 2. Filtrar rutas candidatas (que conecten origen y destino en el orden correcto)
        List<Route> candidates = activeRoutes.stream()
                .filter(route -> connects(route, originNeighborhood, destNeighborhood))
                .toList();

        if (candidates.isEmpty()) {
            throw new NoRouteAvailableException(originNeighborhood, destNeighborhood);
        }

        // 3. Calcular tiempo para cada candidata y seleccionar la mínima
        return candidates.stream()
                .min(Comparator.comparing(route ->
                    travelTimePort.getTravelTime(originCoord, destCoord, route.path())))
                .orElseThrow(() -> new NoRouteAvailableException(originNeighborhood, destNeighborhood));
    }

    private boolean connects(Route route, String origin, String destination) {
        List<String> path = route.neighborhoods();
        int originIdx = path.indexOf(origin);
        int destIdx = path.indexOf(destination);

        // Debe contener ambos barrios y el origen debe estar antes que el destino
        return originIdx != -1 && destIdx != -1 && originIdx < destIdx;
    }
}
