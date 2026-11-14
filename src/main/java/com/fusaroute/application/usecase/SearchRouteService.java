package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidSearchPointException;
import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.exception.TravelTimeUnavailableException;
import com.fusaroute.domain.model.BoardingPoint;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.NearestRoute;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.ResolvedPoint;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteMatch;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.SearchPoint;
import com.fusaroute.domain.model.SuspendedRoute;
import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import com.fusaroute.domain.port.out.ActiveSuspensionsPort;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.RouteSpeedPort;
import com.fusaroute.domain.port.out.TrafficSectorRepositoryPort;
import com.fusaroute.domain.port.out.TravelTimePort;
import com.fusaroute.domain.port.out.UsageEventRecorderPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Busqueda de ruta por GEOMETRIA (RF-04, P1).
 *
 * <ol>
 *   <li>Cada punta (lugar canonico, coordenada o nombre legado) se resuelve a una coordenada.</li>
 *   <li>Cada ruta activa con trazado proyecta las dos puntas; es candidata si el abordaje queda ANTES
 *       que la bajada y ambas puntas estan a menos de 800 m en linea recta del trazado. Si ninguna
 *       cumple, se ofrece la mejor con hasta 1.500 m y {@code longWalk}.</li>
 *   <li>Se rankea por tiempo propio puerta a puerta: caminata (x1,3, 5 km/h) + recorrido sobre el
 *       trazado / velocidad de la ruta (15 km/h urbana, 30 km/h intermunicipal, o la calibrada con
 *       las mediciones de Google).</li>
 *   <li>Solo la ganadora se cronometra con Google, con una llamada como maximo. Si falla, el tiempo
 *       sigue siendo el estimado: siempre hay un numero de minutos.</li>
 * </ol>
 *
 * Las rutas sin trazado (hoy Villa Leny - SENA y Fusagasuga - Chinauta) solo se pueden encontrar por
 * el camino legado de nombres, y nunca le ganan a una ruta con trazado.
 */
public class SearchRouteService implements SearchRouteUseCase {

    private static final Logger log = LoggerFactory.getLogger(SearchRouteService.class);

    private static final String LATENCY_METRIC = "fusaroute.search.route.latency";

    /** Velocidades de referencia cuando la ruta no tiene calibracion propia. */
    static final double URBAN_KMH = 15;
    static final double INTERCITY_KMH = 30;

    /** Muestras que alimentan la mediana y minimo para que cuente (V22). */
    static final int SPEED_SAMPLES = 50;
    static final int MIN_SPEED_SAMPLES = 5;

    /** Una velocidad fuera de este rango es un dato malo (trazado mal medido, Google respondiendo otra cosa). */
    private static final double MIN_PLAUSIBLE_KMH = 3;
    private static final double MAX_PLAUSIBLE_KMH = 90;

    private final RouteRepositoryPort routeRepository;
    private final PlaceRepositoryPort placeRepository;
    private final TravelTimePort travelTimePort;
    private final TrafficSectorRepositoryPort trafficSectorRepository;
    private final RouteSpeedPort routeSpeedPort;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private final UsageEventRecorderPort usageEvents;
    private final ActiveSuspensionsPort suspensions;

    /** Sin registro de eventos de uso: para pruebas que no miden el uso. */
    public SearchRouteService(RouteRepositoryPort routeRepository, PlaceRepositoryPort placeRepository,
                              TravelTimePort travelTimePort, TrafficSectorRepositoryPort trafficSectorRepository,
                              RouteSpeedPort routeSpeedPort, MeterRegistry meterRegistry, Clock clock) {
        this(routeRepository, placeRepository, travelTimePort, trafficSectorRepository, routeSpeedPort,
                meterRegistry, clock, event -> { });
    }

    /** Sin suspensiones: para pruebas que no las miden. */
    public SearchRouteService(RouteRepositoryPort routeRepository, PlaceRepositoryPort placeRepository,
                              TravelTimePort travelTimePort, TrafficSectorRepositoryPort trafficSectorRepository,
                              RouteSpeedPort routeSpeedPort, MeterRegistry meterRegistry, Clock clock,
                              UsageEventRecorderPort usageEvents) {
        this(routeRepository, placeRepository, travelTimePort, trafficSectorRepository, routeSpeedPort,
                meterRegistry, clock, usageEvents, now -> List.of());
    }

    public SearchRouteService(RouteRepositoryPort routeRepository, PlaceRepositoryPort placeRepository,
                              TravelTimePort travelTimePort, TrafficSectorRepositoryPort trafficSectorRepository,
                              RouteSpeedPort routeSpeedPort, MeterRegistry meterRegistry, Clock clock,
                              UsageEventRecorderPort usageEvents, ActiveSuspensionsPort suspensions) {
        this.suspensions = suspensions;
        this.routeRepository = routeRepository;
        this.placeRepository = placeRepository;
        this.travelTimePort = travelTimePort;
        this.trafficSectorRepository = trafficSectorRepository;
        this.routeSpeedPort = routeSpeedPort;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.usageEvents = usageEvents;
    }

    @Override
    public RouteSearchResult findBestRoute(SearchPoint origin, SearchPoint destination) {
        return measured(origin, destination, true);
    }

    @Override
    public RouteSearchResult findBestRouteOffline(SearchPoint origin, SearchPoint destination) {
        return measured(origin, destination, false);
    }

    /**
     * Busca, mide la latencia y deja un evento {@code SEARCH} en {@code search_events} (SCRUM-186),
     * tanto si hubo ruta como si no o si fallo. El registro es asincrono: la busqueda no espera por el.
     */
    private RouteSearchResult measured(SearchPoint origin, SearchPoint destination, boolean online) {
        Instant startedAt = clock.instant();
        Timer.Sample sample = Timer.start(meterRegistry);
        RouteSearchResult result = null;
        RuntimeException failure = null;
        try {
            result = search(origin, destination, online);
            return result;
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            // Etiquetado por modo a proposito: el p95 < 8 s de RNF-01 es sobre la busqueda que
            // consulta a Google; mezclar la offline en la misma serie haria ver un p95 mejor del real.
            long nanos = sample.stop(meterRegistry.timer(LATENCY_METRIC, "mode", online ? "online" : "offline"));
            recordSearch(startedAt, origin, destination, result, failure, nanos);
        }
    }

    private void recordSearch(Instant startedAt, SearchPoint originPoint, SearchPoint destinationPoint,
                              RouteSearchResult result, RuntimeException failure, long nanos) {
        try {
            int latencyMs = (int) Math.min(Integer.MAX_VALUE, Math.max(0, nanos / 1_000_000));
            UsageEvent event;
            if (result != null) {
                event = UsageEvent.of(startedAt, UsageEventType.SEARCH,
                        labelOf(result.origin(), originPoint), labelOf(result.destination(), destinationPoint),
                        result.route().id(), result.longWalk() ? SearchOutcome.LONG_WALK : SearchOutcome.FOUND,
                        result.etaSource(), latencyMs, !result.congestionSectors().isEmpty());
            } else if (failure instanceof NoRouteAvailableException noRoute) {
                event = UsageEvent.of(startedAt, UsageEventType.SEARCH,
                        firstNonNull(noRoute.originLabel(), originPoint), firstNonNull(noRoute.destinationLabel(), destinationPoint),
                        null, SearchOutcome.NOT_FOUND, null, latencyMs, false);
            } else {
                event = UsageEvent.of(startedAt, UsageEventType.SEARCH,
                        rawLabel(originPoint), rawLabel(destinationPoint),
                        null, SearchOutcome.ERROR, null, latencyMs, false);
            }
            usageEvents.record(event);
        } catch (RuntimeException e) {
            // Una estadistica nunca tumba una busqueda.
            log.debug("No se pudo registrar el evento de busqueda: {}", e.getMessage());
        }
    }

    private static String labelOf(ResolvedPoint resolved, SearchPoint raw) {
        return resolved != null && resolved.label() != null ? resolved.label() : rawLabel(raw);
    }

    private static String firstNonNull(String label, SearchPoint raw) {
        return label != null ? label : rawLabel(raw);
    }

    /**
     * Etiqueta de una punta sin resolver. Una coordenada NO se guarda: es la ubicacion de una persona,
     * y search_events no lleva datos personales.
     */
    private static String rawLabel(SearchPoint point) {
        return switch (point) {
            case null -> null;
            case SearchPoint.OfPlace p -> "lugar #" + p.placeId();
            case SearchPoint.OfCoordinate c -> "Coordenada";
            case SearchPoint.OfName n -> n.name();
        };
    }

    // ------------------------------------------------------------------------------------------
    // Busqueda
    // ------------------------------------------------------------------------------------------

    private RouteSearchResult search(SearchPoint originPoint, SearchPoint destinationPoint, boolean online) {
        List<Place> places = placeRepository.findAll();
        ResolvedPoint origin = resolve(originPoint, "origin", places);
        ResolvedPoint destination = resolve(destinationPoint, "destination", places);
        if (origin.place() != null && destination.place() != null
                && origin.place().slug().equals(destination.place().slug())) {
            throw new InvalidSearchPointException("destination", "es el mismo lugar que el origen (" + origin.label() + ")");
        }

        List<Route> routes = routeRepository.findAll().stream().filter(Route::isActive).toList();
        Map<Long, Double> calibrated = calibratedSpeeds();

        Optional<Ranked> best = Optional.empty();
        boolean longWalk = false;
        if (origin.hasCoordinate() && destination.hasCoordinate()) {
            best = bestMatch(routes, origin, destination, calibrated, BoardingPoint.MAX_WALKING_DISTANCE_METERS);
            if (best.isEmpty()) {
                best = bestMatch(routes, origin, destination, calibrated, BoardingPoint.LONG_WALK_DISTANCE_METERS);
                longWalk = best.isPresent();
            }
        }

        if (best.isPresent()) {
            return geometricResult(best.get(), origin, destination, longWalk, online, calibrated);
        }
        return legacyByName(routes, origin, destination)
                .orElseThrow(() -> noRoute(routes, origin, destination, originPoint, destinationPoint));
    }

    /** La ruta con menor tiempo puerta a puerta que cumple el umbral de caminata. */
    private Optional<Ranked> bestMatch(List<Route> routes, ResolvedPoint origin, ResolvedPoint destination,
                                       Map<Long, Double> calibrated, double maxStraightMeters) {
        return routes.stream()
                .filter(Route::hasPath)
                .map(route -> route.matchBetween(origin.coordinate(), destination.coordinate(), maxStraightMeters))
                .flatMap(Optional::stream)
                .map(match -> new Ranked(match, doorToDoor(match, calibrated)))
                .min(Comparator.comparing(Ranked::doorToDoor)
                        // Empate exacto: la de menos caminata, y luego la de menor id, para que el
                        // resultado no dependa del orden en que la base devuelva las filas.
                        .thenComparingDouble(r -> r.match().walkingDistanceInMeters())
                        .thenComparing(r -> r.match().route().id(), Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private static Duration doorToDoor(RouteMatch match, Map<Long, Double> calibrated) {
        return match.boarding().approxWalkingTime()
                .plus(rideEstimate(match, calibrated))
                .plus(match.dropOff().approxWalkingTime());
    }

    /** Recorrido sobre el trazado dividido por la velocidad de la ruta. */
    private static Duration rideEstimate(RouteMatch match, Map<Long, Double> calibrated) {
        double kmh = calibrated.getOrDefault(match.route().id(), defaultKmh(match.route()));
        return Duration.ofSeconds(Math.round(match.rideDistanceInMeters() / (kmh * 1000d / 3600d)));
    }

    private static double defaultKmh(Route route) {
        return route.type() == RouteType.INTERMUNICIPAL ? INTERCITY_KMH : URBAN_KMH;
    }

    /** Mediana de las ultimas 50 velocidades de cada ruta con al menos 5 muestras. */
    private Map<Long, Double> calibratedSpeeds() {
        Map<Long, Double> out = new HashMap<>();
        try {
            routeSpeedPort.recentObservations(SPEED_SAMPLES).forEach((routeId, samples) -> {
                if (samples.size() >= MIN_SPEED_SAMPLES) {
                    out.put(routeId, median(samples));
                }
            });
        } catch (RuntimeException e) {
            // La calibracion mejora el ranking pero no es indispensable: sin ella se usan las
            // velocidades fijas.
            log.warn("No se pudo leer la calibracion de velocidades: {}", e.getMessage());
        }
        return out;
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    // ------------------------------------------------------------------------------------------
    // Resultado
    // ------------------------------------------------------------------------------------------

    private RouteSearchResult geometricResult(Ranked winner, ResolvedPoint origin, ResolvedPoint destination,
                                              boolean longWalk, boolean online,
                                              Map<Long, Double> calibrated) {
        RouteMatch match = winner.match();
        Route route = match.route();
        List<Coordinate> ridePath = match.ridePath();

        Duration ride = ceilToMinutes(rideEstimate(match, calibrated));
        EtaSource etaSource = EtaSource.ESTIMATED;
        if (online) {
            // Una sola llamada por busqueda, y solo para la ganadora: con Google solo se cronometra,
            // nunca se elige (RNF-03).
            try {
                Duration google = travelTimePort.getTravelTime(match.boarding().point(), match.dropOff().point(), ridePath);
                ride = ceilToMinutes(google);
                etaSource = EtaSource.TRAFFIC;
                recordObservedSpeed(route, match.rideDistanceInMeters(), google);
            } catch (TravelTimeUnavailableException e) {
                log.warn("Ruta '{}' sin tiempo de Google, se usa el estimado: {}", route.name(), e.getMessage());
            }
        }

        LocalTime now = LocalTime.now(clock);
        List<TrafficSector> congestion = trafficSectorRepository.findAll().stream()
                .filter(TrafficSector::hasCircle)
                .filter(sector -> sector.isWarningActiveAt(now))
                .filter(sector -> sector.isCrossedBy(ridePath))
                .toList();

        return new RouteSearchResult(route.withCurrentFares(LocalDate.now(clock)), ride,
                match.rideDistanceInMeters(), match.boarding(), match.dropOff(),
                etaSource, longWalk, congestion, origin, destination);
    }

    private void recordObservedSpeed(Route route, double rideMeters, Duration googleTime) {
        if (route.id() == null || googleTime.isZero()) {
            return;
        }
        double kmh = rideMeters / googleTime.toSeconds() * 3.6;
        if (kmh < MIN_PLAUSIBLE_KMH || kmh > MAX_PLAUSIBLE_KMH) {
            return;
        }
        try {
            routeSpeedPort.record(route.id(), Math.round(kmh * 100) / 100d);
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar la velocidad observada de la ruta {}: {}", route.id(), e.getMessage());
        }
    }

    /** El estimado se muestra en minutos enteros y nunca en cero: un viaje que dura 0 min no existe. */
    private static Duration ceilToMinutes(Duration duration) {
        long minutes = Math.max(1, (duration.toSeconds() + 59) / 60);
        return Duration.ofMinutes(minutes);
    }

    // ------------------------------------------------------------------------------------------
    // Camino legado: rutas sin trazado, por nombre
    // ------------------------------------------------------------------------------------------

    /**
     * Rutas SIN trazado: el origen debe ir antes que el destino en su lista de lugares. Solo se llega
     * aqui si ninguna ruta con trazado sirvio, asi que nunca le gana a una con trazado. Sin trazado no
     * hay abordaje, bajada, recorrido ni congestion: esos campos quedan vacios, y el tiempo tambien,
     * porque no hay con que estimarlo.
     */
    private Optional<RouteSearchResult> legacyByName(List<Route> routes, ResolvedPoint origin, ResolvedPoint destination) {
        List<String> originTerms = termsOf(origin);
        List<String> destinationTerms = termsOf(destination);
        if (originTerms.isEmpty() || destinationTerms.isEmpty()) {
            return Optional.empty();
        }
        return routes.stream()
                .filter(route -> !route.hasPath())
                .filter(route -> servesByName(route, originTerms, destinationTerms))
                .findFirst()
                .map(route -> new RouteSearchResult(route.withCurrentFares(LocalDate.now(clock)), null, null, null, null,
                        EtaSource.ESTIMATED, false, List.of(), origin, destination));
    }

    /** El origen va antes que el destino en los lugares de la ruta, por nombre. */
    private static boolean servesByName(Route route, List<String> originTerms, List<String> destinationTerms) {
        return firstIndex(route, originTerms).isPresent() && lastIndex(route, destinationTerms).isPresent()
                && firstIndex(route, originTerms).getAsInt() < lastIndex(route, destinationTerms).getAsInt();
    }

    private static List<String> termsOf(ResolvedPoint point) {
        if (point.place() != null) {
            return point.place().allNames();
        }
        return point.coordinate() == null && point.label() != null ? List.of(point.label()) : List.of();
    }

    private static OptionalInt firstIndex(Route route, List<String> terms) {
        return terms.stream().map(route::indexOfPlace).filter(OptionalInt::isPresent)
                .mapToInt(OptionalInt::getAsInt).min();
    }

    private static OptionalInt lastIndex(Route route, List<String> terms) {
        return terms.stream().map(route::lastIndexOfPlace).filter(OptionalInt::isPresent)
                .mapToInt(OptionalInt::getAsInt).max();
    }

    // ------------------------------------------------------------------------------------------
    // Resolucion de puntas
    // ------------------------------------------------------------------------------------------

    private ResolvedPoint resolve(SearchPoint point, String side, List<Place> places) {
        return switch (point) {
            case SearchPoint.OfPlace p -> {
                Place place = places.stream().filter(x -> x.id() != null && x.id() == p.placeId()).findFirst()
                        .or(() -> placeRepository.findById(p.placeId()))
                        .orElseThrow(() -> new InvalidSearchPointException(side, "no existe el lugar con id " + p.placeId()));
                yield new ResolvedPoint(place.coordinate(), place.name(), place);
            }
            case SearchPoint.OfCoordinate c -> new ResolvedPoint(c.coordinate(), nearLabel(c.coordinate(), places), null);
            case SearchPoint.OfName n -> {
                List<Place> hits = places.stream().filter(x -> x.matchesName(n.name())).toList();
                if (hits.size() == 1) {
                    yield new ResolvedPoint(hits.get(0).coordinate(), hits.get(0).name(), hits.get(0));
                }
                if (hits.size() > 1) {
                    throw new InvalidSearchPointException(side, "\"" + n.name() + "\" corresponde a varios lugares ("
                            + hits.stream().map(Place::name).sorted().toList() + "); elige uno con su id");
                }
                if (n.name().isBlank()) {
                    throw new InvalidSearchPointException(side, "falta el lugar");
                }
                // Sin lugar con coordenada: puede servir contra una ruta sin trazado, por nombre.
                yield new ResolvedPoint(null, n.name(), null);
            }
        };
    }

    private static String nearLabel(Coordinate coordinate, List<Place> places) {
        return places.stream()
                .min(Comparator.comparingDouble(place -> place.coordinate().distanceInMetersTo(coordinate)))
                .map(place -> "Cerca de " + place.name())
                .orElse("Mi ubicación");
    }

    // ------------------------------------------------------------------------------------------
    // Sin ruta
    // ------------------------------------------------------------------------------------------

    private RuntimeException noRoute(List<Route> routes, ResolvedPoint origin, ResolvedPoint destination,
                                              SearchPoint originPoint, SearchPoint destinationPoint) {
        // Un nombre que no es un lugar conocido ni sirve contra una ruta sin trazado es un dato malo
        // del cliente, no un "no hay ruta".
        if (!origin.hasCoordinate() && origin.place() == null) {
            return unresolved("origin", origin);
        }
        if (!destination.hasCoordinate() && destination.place() == null) {
            return unresolved("destination", destination);
        }
        return new NoRouteAvailableException(origin.label(), destination.label(),
                nearest(routes, origin), nearest(routes, destination),
                suspendedCandidates(origin, destination), clock.getZone());
    }

    /**
     * Rutas que habrian servido el trayecto pero estan efectivamente suspendidas (RF-12). Solo se llama
     * cuando ninguna ruta activa sirvio, asi que si la lista no esta vacia, "la unica opcion estaba suspendida".
     * "Candidata" usa la misma regla que la busqueda: con trazado, abordaje antes que bajada a no mas de
     * 1.500 m; sin trazado, origen antes que destino por nombre.
     *
     * <p>Es un dato de cortesia: si falla, el 404 sale igual que antes en vez de convertirse en un 500.
     */
    private List<SuspendedRoute> suspendedCandidates(ResolvedPoint origin, ResolvedPoint destination) {
        try {
            Map<Long, Instant> endsByRoute = new LinkedHashMap<>();
            suspensions.findEffectiveAt(clock.instant()).stream()
                    .sorted(Comparator.comparing(RouteSuspension::endsAt))
                    .forEach(s -> endsByRoute.putIfAbsent(s.routeId(), s.endsAt()));
            if (endsByRoute.isEmpty()) {
                return List.of();
            }
            return routeRepository.findAll().stream()
                    .filter(route -> route.status() == RouteStatus.SUSPENDIDA && endsByRoute.containsKey(route.id()))
                    .filter(route -> couldServe(route, origin, destination))
                    .map(route -> new SuspendedRoute(route.id(), route.name(), endsByRoute.get(route.id())))
                    .sorted(Comparator.comparing(SuspendedRoute::endsAt).thenComparing(SuspendedRoute::routeId))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("No se pudieron consultar las rutas suspendidas: {}", e.getMessage());
            return List.of();
        }
    }

    private static boolean couldServe(Route route, ResolvedPoint origin, ResolvedPoint destination) {
        if (route.hasPath()) {
            return origin.hasCoordinate() && destination.hasCoordinate()
                    && route.matchBetween(origin.coordinate(), destination.coordinate(),
                            BoardingPoint.LONG_WALK_DISTANCE_METERS).isPresent();
        }
        List<String> originTerms = termsOf(origin);
        List<String> destinationTerms = termsOf(destination);
        return !originTerms.isEmpty() && !destinationTerms.isEmpty()
                && servesByName(route, originTerms, destinationTerms);
    }

    private static RuntimeException unresolved(String side, ResolvedPoint point) {
        return new InvalidSearchPointException(side, "\"" + point.label() + "\" no es un lugar conocido");
    }

    private static NearestRoute nearest(List<Route> routes, ResolvedPoint point) {
        if (!point.hasCoordinate()) {
            return null;
        }
        NearestRoute best = null;
        for (Route route : routes) {
            if (!route.hasPath()) {
                continue;
            }
            var distance = route.distanceToPathInMeters(point.coordinate());
            if (distance.isPresent()) {
                long walk = Math.round(distance.getAsDouble() * BoardingPoint.WALKING_DETOUR_FACTOR);
                if (best == null || walk < best.distanceInMeters()) {
                    best = new NearestRoute(route.name(), walk);
                }
            }
        }
        return best;
    }

    private record Ranked(RouteMatch match, Duration doorToDoor) {
    }
}
