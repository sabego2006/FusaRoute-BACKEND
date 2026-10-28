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
import java.util.OptionalDouble;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Implementación del caso de uso de búsqueda de ruta.
 * Orquesta el filtrado de rutas candidatas y la selección de la mejor basada en tiempo.
 */
public class SearchRouteService implements SearchRouteUseCase {

    private static final String LATENCY_METRIC = "fusaroute.search.route.latency";

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
            List<Route> candidates = candidatesFor(originNeighborhood, originCoord, destNeighborhood, destCoord);

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
            return RouteSearchResult.online(bestRoute, bestTime,
                    bestRoute.boardingPointFor(originCoord).orElse(null),
                    bestRoute.boardingPointFor(destCoord).orElse(null),
                    travelDistanceOrNull(bestRoute, originCoord, destCoord));
        } finally {
            // Etiquetado por modo a proposito: el p95 < 8 s de RNF-01 es sobre la
            // busqueda que consulta a Google. La offline no sale a la red y es ordenes
            // de magnitud mas rapida, asi que mezclarlas en la misma serie haria ver un
            // p95 mejor del real.
            sample.stop(meterRegistry.timer(LATENCY_METRIC, "mode", "online"));
        }
    }

    @Override
    public RouteSearchResult findBestRouteOffline(String originNeighborhood, Coordinate originCoord,
                               String destNeighborhood, Coordinate destCoord) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<Route> candidates = candidatesFor(originNeighborhood, originCoord, destNeighborhood, destCoord);

            Route bestRoute = null;
            double bestDistance = Double.MAX_VALUE;
            for (Route candidate : candidates) {
                // Sin trazado no hay con que medir, asi que queda de ultima: entra solo
                // si ninguna otra candidata pudo compararse.
                double distance = candidate.travelDistanceInMeters(originCoord, destCoord)
                        .orElse(Double.MAX_VALUE);
                if (bestRoute == null || distance < bestDistance) {
                    bestRoute = candidate;
                    bestDistance = distance;
                }
            }
            // Si gano una ruta sin trazado, la distancia queda vacia en vez de 0: no se
            // midio nada, y un 0 se leeria como "ya estas encima de la ruta".
            return RouteSearchResult.offline(bestRoute,
                    bestDistance == Double.MAX_VALUE ? null : bestDistance,
                    bestRoute.boardingPointFor(originCoord).orElse(null),
                    bestRoute.boardingPointFor(destCoord).orElse(null));
        } finally {
            sample.stop(meterRegistry.timer(LATENCY_METRIC, "mode", "offline"));
        }
    }

    /**
     * Rutas que conectan origen y destino en el orden correcto y a las que se puede
     * llegar a pie (RF-04). Las rutas sin trazado pasan el filtro de distancia: no hay
     * con que medirlas, y descartarlas por un dato que falta las haria invisibles sin
     * una razon de negocio.
     */
    private List<Route> candidatesFor(String originNeighborhood, Coordinate originCoord,
                                      String destNeighborhood, Coordinate destCoord) {
        List<Route> candidates = routeRepository.findAll().stream()
                .filter(Route::isActive)
                .filter(route -> connects(route, originNeighborhood, destNeighborhood))
                .filter(route -> route.isWithinWalkingDistance(originCoord, destCoord))
                .toList();

        if (candidates.isEmpty()) {
            throw new NoRouteAvailableException(originNeighborhood, destNeighborhood);
        }
        return candidates;
    }

    private static Double travelDistanceOrNull(Route route, Coordinate originCoord, Coordinate destCoord) {
        OptionalDouble distance = route.travelDistanceInMeters(originCoord, destCoord);
        return distance.isPresent() ? distance.getAsDouble() : null;
    }

    private boolean connects(Route route, String origin, String destination) {
        List<String> path = route.neighborhoods();
        int originIdx = path.indexOf(origin);
        int destIdx = path.indexOf(destination);

        // Debe contener ambos barrios y el origen debe estar antes que el destino
        return originIdx != -1 && destIdx != -1 && originIdx < destIdx;
    }
}
