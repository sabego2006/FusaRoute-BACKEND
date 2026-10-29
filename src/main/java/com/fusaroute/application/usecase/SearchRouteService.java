package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.model.BoardingPoint;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.TravelTimePort;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Implementación del caso de uso de búsqueda de ruta.
 * Orquesta el filtrado de rutas candidatas y la selección de la mejor basada en tiempo.
 */
public class SearchRouteService implements SearchRouteUseCase {

    private static final String LATENCY_METRIC = "fusaroute.search.route.latency";

    /**
     * Centinela interno para ordenar candidatas sin tiempo real conocido (ver el
     * comentario en {@link #findBestRoute}). Nunca se expone: {@code bestBusTime} se
     * reporta aparte, y queda en {@code null} si la candidata ganadora nunca tuvo un
     * tiempo real.
     */
    private static final Duration UNKNOWN_TIME_RANK = Duration.ofDays(1);

    private final RouteRepositoryPort routeRepository;
    private final TravelTimePort travelTimePort;
    private final MeterRegistry meterRegistry;

    public SearchRouteService(RouteRepositoryPort routeRepository, TravelTimePort travelTimePort, MeterRegistry meterRegistry) {
        this.routeRepository = routeRepository;
        this.travelTimePort = travelTimePort;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RouteSearchResult findBestRoute(String originNeighborhood, Optional<Coordinate> originCoord,
                               String destNeighborhood, Optional<Coordinate> destCoord) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<Route> candidates = candidatesFor(originNeighborhood, originCoord, destNeighborhood, destCoord);

            // Se cronometra el TRAMO EN BUSETA de cada candidata, de su abordaje a su
            // bajada, no el par origen-destino del usuario: Distance Matrix no sigue el
            // trazado (cotiza por par de puntos), asi que lo unico que distingue a una
            // ruta de otra es por donde se toma y por donde se deja. Cronometrar siempre
            // origen->destino daria el mismo tiempo a todas y "la mejor" seria la primera
            // del repositorio, no la mas rapida.
            //
            // Y se elige por tiempo puerta a puerta = caminata al abordaje + buseta +
            // caminata desde la bajada: una buseta un poco mas rapida no compensa si deja
            // al usuario a 700 m del destino. La caminata es geometria del dominio
            // (BoardingPoint, a 5 km/h), no una llamada a Google.
            //
            // Un bucle en vez de Comparator.comparing(keyExtractor): ese extractor se
            // reevalua en cada comparacion, llamando a travelTimePort mas veces de las
            // necesarias; ademas, aqui hacen falta el Duration ganador y sus puntos.
            Route bestRoute = null;
            Duration bestBusTime = null;
            Optional<BoardingPoint> bestBoarding = Optional.empty();
            Optional<BoardingPoint> bestDropOff = Optional.empty();
            Duration bestDoorToDoorRank = null;
            for (Route candidate : candidates) {
                // SCRUM-130: el cliente ya no esta obligado a mandar coordenadas. Si no
                // las mando, se deriva un punto aproximado del nombre del barrio sobre
                // el trazado propio de ESTA candidata (cada ruta reparte sus barrios
                // distinto, asi que el punto derivado no es el mismo para todas).
                Optional<Coordinate> originPoint = originCoord.or(() -> candidate.pointAtNeighborhood(originNeighborhood));
                Optional<Coordinate> destPoint = destCoord.or(() -> candidate.pointAtNeighborhood(destNeighborhood));

                Optional<BoardingPoint> boarding = originPoint.flatMap(candidate::boardingPointFor);
                Optional<BoardingPoint> dropOff = destPoint.flatMap(candidate::boardingPointFor);

                // Sin trazado no hay abordaje ni bajada que proyectar: se cronometra el
                // punto crudo (del cliente o derivado) de cada punta, sin caminata.
                Coordinate from = boarding.map(BoardingPoint::point).or(() -> originPoint).orElse(null);
                Coordinate to = dropOff.map(BoardingPoint::point).or(() -> destPoint).orElse(null);

                // Sin las dos puntas resueltas no hay par de puntos que cronometrar con
                // Google: esta candidata queda sin tiempo real conocido, igual que hoy
                // una ruta sin trazado queda sin ultima milla. No se descarta por esto —
                // solo pierde siempre frente a cualquier candidata que si tenga tiempo.
                Duration busTime = (from != null && to != null)
                        ? travelTimePort.getTravelTime(from, to, candidate.path())
                        : null;
                Duration doorToDoorRank = busTime == null
                        ? UNKNOWN_TIME_RANK
                        : walkingTime(boarding).plus(busTime).plus(walkingTime(dropOff));

                if (bestDoorToDoorRank == null || doorToDoorRank.compareTo(bestDoorToDoorRank) < 0) {
                    bestRoute = candidate;
                    bestBusTime = busTime;
                    bestBoarding = boarding;
                    bestDropOff = dropOff;
                    bestDoorToDoorRank = doorToDoorRank;
                }
            }
            // estimatedTime es el tramo en buseta, no el puerta a puerta: la caminata ya
            // viaja aparte en cada BoardingPoint, y el front la suma con esos datos.
            // Queda en null si la ganadora nunca tuvo un tiempo real que reportar.
            return RouteSearchResult.online(bestRoute, bestBusTime,
                    bestBoarding.orElse(null), bestDropOff.orElse(null),
                    travelDistanceOrNull(bestBoarding, bestDropOff));
        } finally {
            // Etiquetado por modo a proposito: el p95 < 8 s de RNF-01 es sobre la
            // busqueda que consulta a Google. La offline no sale a la red y es ordenes
            // de magnitud mas rapida, asi que mezclarlas en la misma serie haria ver un
            // p95 mejor del real.
            sample.stop(meterRegistry.timer(LATENCY_METRIC, "mode", "online"));
        }
    }

    @Override
    public RouteSearchResult findBestRouteOffline(String originNeighborhood, Optional<Coordinate> originCoord,
                               String destNeighborhood, Optional<Coordinate> destCoord) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<Route> candidates = candidatesFor(originNeighborhood, originCoord, destNeighborhood, destCoord);

            Route bestRoute = null;
            double bestDistance = Double.MAX_VALUE;
            Optional<BoardingPoint> bestBoarding = Optional.empty();
            Optional<BoardingPoint> bestDropOff = Optional.empty();
            for (Route candidate : candidates) {
                // El modo offline nunca manda coordenadas (SCRUM-130): el unico origen
                // de un punto es el nombre del barrio proyectado sobre el trazado propio
                // de esta candidata, asi que hoy esto solo resuelve en las rutas que ya
                // tienen trazado cargado.
                Optional<Coordinate> originPoint = originCoord.or(() -> candidate.pointAtNeighborhood(originNeighborhood));
                Optional<Coordinate> destPoint = destCoord.or(() -> candidate.pointAtNeighborhood(destNeighborhood));

                // Sin con que medir, queda de ultima: entra solo si ninguna otra
                // candidata pudo compararse.
                double distance = (originPoint.isPresent() && destPoint.isPresent())
                        ? candidate.travelDistanceInMeters(originPoint.get(), destPoint.get()).orElse(Double.MAX_VALUE)
                        : Double.MAX_VALUE;

                if (bestRoute == null || distance < bestDistance) {
                    bestRoute = candidate;
                    bestDistance = distance;
                    bestBoarding = originPoint.flatMap(candidate::boardingPointFor);
                    bestDropOff = destPoint.flatMap(candidate::boardingPointFor);
                }
            }
            // Si gano una ruta sin trazado, la distancia queda vacia en vez de 0: no se
            // midio nada, y un 0 se leeria como "ya estas encima de la ruta".
            return RouteSearchResult.offline(bestRoute,
                    bestDistance == Double.MAX_VALUE ? null : bestDistance,
                    bestBoarding.orElse(null),
                    bestDropOff.orElse(null));
        } finally {
            sample.stop(meterRegistry.timer(LATENCY_METRIC, "mode", "offline"));
        }
    }

    /**
     * Rutas que conectan origen y destino en el orden correcto y a las que se puede
     * llegar a pie (RF-04). Las rutas sin trazado pasan el filtro de distancia: no hay
     * con que medirlas, y descartarlas por un dato que falta las haria invisibles sin
     * una razon de negocio.
     *
     * El filtro de distancia solo evalua las coordenadas que el CLIENTE mando
     * (SCRUM-130): un punto derivado del nombre del barrio esta siempre sobre el
     * trazado, asi que aplicarselo seria dejar de filtrar sin decirlo.
     */
    private List<Route> candidatesFor(String originNeighborhood, Optional<Coordinate> originCoord,
                                      String destNeighborhood, Optional<Coordinate> destCoord) {
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

    /** Caminata hasta/desde un punto del trazado; cero si la ruta no tiene trazado. */
    private static Duration walkingTime(Optional<BoardingPoint> point) {
        return point.map(BoardingPoint::walkingTime).orElse(Duration.ZERO);
    }

    /**
     * Recorrido en buseta entre abordaje y bajada, reusando los puntos ya proyectados en
     * el bucle. Vacio si la ruta no tiene trazado: no se midio nada, que es distinto de
     * cero metros.
     */
    private static Double travelDistanceOrNull(Optional<BoardingPoint> boarding, Optional<BoardingPoint> dropOff) {
        if (boarding.isEmpty() || dropOff.isEmpty()) {
            return null;
        }
        return Math.abs(dropOff.get().distanceAlongPathInMeters() - boarding.get().distanceAlongPathInMeters());
    }

    private boolean connects(Route route, String origin, String destination) {
        List<String> path = route.neighborhoods();
        int originIdx = path.indexOf(origin);
        int destIdx = path.indexOf(destination);

        // Debe contener ambos barrios y el origen debe estar antes que el destino
        return originIdx != -1 && destIdx != -1 && originIdx < destIdx;
    }
}
