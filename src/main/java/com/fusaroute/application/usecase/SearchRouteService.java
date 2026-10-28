package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.TravelTimePort;

import java.time.Duration;
import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Implementación del caso de uso de búsqueda de ruta.
 * Orquesta el filtrado de rutas candidatas y la selección de la mejor basada en tiempo.
 */
public class SearchRouteService implements SearchRouteUseCase {

    private final RouteRepositoryPort routeRepository;
    private final TravelTimePort travelTimePort;
    private final MeterRegistry meterRegistry;

    public SearchRouteService(RouteRepositoryPort routeRepository, TravelTimePort travelTimePort, MeterRegistry meterRegistry) {
        this.routeRepository = routeRepository;
        this.travelTimePort = travelTimePort;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RouteSearchResult findBestRoute(String originNeighborhood, Coordinate originCoord,
                               String destNeighborhood, Coordinate destCoord) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 1. Obtener todas las rutas activas
            List<Route> activeRoutes = routeRepository.findAll().stream()
                    .filter(Route::isActive)
                    .toList();

            // 2. Filtrar rutas candidatas: que conecten origen y destino en el orden
            // correcto y que no obliguen a caminar mas de 800 m en ninguna de las dos
            // puntas (RF-04). Las rutas sin trazado pasan este segundo filtro: no hay
            // con que medirlas, y descartarlas por un dato que falta las haria
            // invisibles sin una razon de negocio.
            List<Route> candidates = activeRoutes.stream()
                    .filter(route -> connects(route, originNeighborhood, destNeighborhood))
                    .filter(route -> route.isWithinWalkingDistance(originCoord, destCoord))
                    .toList();

            if (candidates.isEmpty()) {
                throw new NoRouteAvailableException(originNeighborhood, destNeighborhood);
            }

            // 3. Calcular tiempo para cada candidata y quedarse con la de menor tiempo.
            // Un bucle en vez de Comparator.comparing(keyExtractor): ese extractor se
            // reevalua en cada comparacion, llamando a travelTimePort mas veces de las
            // necesarias; ademas, aqui hace falta el Duration ganador, no solo la ruta.
            Route bestRoute = null;
            Duration bestTime = null;
            for (Route candidate : candidates) {
                Duration time = travelTimePort.getTravelTime(originCoord, destCoord, candidate.path());
                if (bestTime == null || time.compareTo(bestTime) < 0) {
                    bestRoute = candidate;
                    bestTime = time;
                }
            }
            return resultFor(bestRoute, bestTime, originCoord, destCoord);
        } finally {
            sample.stop(meterRegistry.timer("fusaroute.search.route.latency"));
        }
    }

    /**
     * Arma el resultado agregandole la ultima milla. Los puntos quedan en null cuando
     * la ruta no tiene trazado cargado: el viaje en buseta se sugiere igual, solo que
     * sin decir por donde acercarse a pie.
     */
    private RouteSearchResult resultFor(Route route, Duration travelTime,
                                        Coordinate originCoord, Coordinate destCoord) {
        return new RouteSearchResult(route, travelTime,
                route.boardingPointFor(originCoord).orElse(null),
                route.boardingPointFor(destCoord).orElse(null));
    }

    private boolean connects(Route route, String origin, String destination) {
        List<String> path = route.neighborhoods();
        int originIdx = path.indexOf(origin);
        int destIdx = path.indexOf(destination);

        // Debe contener ambos barrios y el origen debe estar antes que el destino
        return originIdx != -1 && destIdx != -1 && originIdx < destIdx;
    }
}
