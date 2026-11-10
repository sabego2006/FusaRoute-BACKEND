package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.exception.InvalidSearchPointException;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.SearchPoint;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.RecordSearchUseCase;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** Catalogo publico de rutas (RF-15, SCRUM-19) y busqueda de la mejor ruta (RF-04, SCRUM-54). Solo lectura y sin sesion. */
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    /**
     * Cache HTTP del catalogo publico (RNF-01, SCRUM-127). Spring Security manda por
     * defecto {@code no-store} en TODA respuesta, y eso es lo correcto para datos de
     * sesion, pero obligaba a repagar el viaje completo al servidor en cada navegacion
     * por un dato que cambia de mes en mes. Spring Security no pisa un Cache-Control que
     * el controlador ya puso, asi que basta fijarlo aqui, solo en estos dos GET.
     *
     * <p>Listado: 10 min. Una ruta suspendida (SCRUM-25) puede seguir apareciendo hasta
     * 10 min, mas 5 de {@code stale-while-revalidate}; es el precio aceptado.
     *
     * <p>Detalle: 60 s, sin stale-while-revalidate. Trae {@code congestionSectors}, que
     * depende de la hora (el aviso se enciende 30 min antes del trancon), asi que servir
     * un detalle de hace 10 min mostraria un aviso vencido.
     *
     * <p>{@code public}: el catalogo no lleva datos de sesion, y el frontend puede
     * mandar el JWT en la peticion sin que eso vuelva privada la respuesta.
     */
    private static final CacheControl LIST_CACHE = CacheControl.maxAge(Duration.ofMinutes(10))
            .staleWhileRevalidate(Duration.ofMinutes(5)).cachePublic();
    private static final CacheControl DETAIL_CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final ListActiveRoutesUseCase listActiveRoutesUseCase;
    private final GetRouteDetailUseCase getRouteDetailUseCase;
    private final GetCongestionWarningUseCase getCongestionWarningUseCase;
    private final GetRouteFareUseCase getRouteFareUseCase;
    private final SearchRouteUseCase searchRouteUseCase;
    private final RecordSearchUseCase recordSearchUseCase;

    public RouteController(ListActiveRoutesUseCase listActiveRoutesUseCase,
                           GetRouteDetailUseCase getRouteDetailUseCase,
                           GetCongestionWarningUseCase getCongestionWarningUseCase,
                           GetRouteFareUseCase getRouteFareUseCase,
                           SearchRouteUseCase searchRouteUseCase,
                           RecordSearchUseCase recordSearchUseCase) {
        this.listActiveRoutesUseCase = listActiveRoutesUseCase;
        this.getRouteDetailUseCase = getRouteDetailUseCase;
        this.getCongestionWarningUseCase = getCongestionWarningUseCase;
        this.getRouteFareUseCase = getRouteFareUseCase;
        this.searchRouteUseCase = searchRouteUseCase;
        this.recordSearchUseCase = recordSearchUseCase;
    }

    /**
     * Anota la busqueda en el historial del usuario (RF-07), solo si hay sesion.
     * Buscar es publico, asi que {@code jwt} llega nulo para un anonimo y entonces
     * no hay historial donde escribir.
     *
     * Se llama despues de que la busqueda tuvo exito: una consulta que termino en
     * 404 no es una busqueda que valga la pena repetir.
     */
    private void recordIfAuthenticated(Jwt jwt, RouteSearchResult result) {
        if (jwt != null && result.origin() != null && result.destination() != null) {
            // El historial guarda el nombre del lugar resuelto (lo que el usuario vio en el selector),
            // no el parametro crudo: un id o unas coordenadas no se pueden volver a mostrar.
            recordSearchUseCase.record(Long.parseLong(jwt.getSubject()),
                    result.origin().label(), result.destination().label());
        }
    }

    @Operation(summary = "Listar rutas activas", description = "Retorna la lista de todas las rutas de buseta que se encuentran activas")
    @ApiResponse(responseCode = "200", description = "Lista de rutas recuperada exitosamente")
    @GetMapping
    public ResponseEntity<List<RouteResponse>> list() {
        return ResponseEntity.ok().cacheControl(LIST_CACHE)
                .body(listActiveRoutesUseCase.listActive().stream().map(RouteResponse::from).toList());
    }

    /**
     * Busca la mejor ruta entre un origen y un destino, por geometria (P1). Va antes de
     * {@code /{id}} en este archivo solo por orden de lectura: Spring ya prefiere el segmento literal
     * "search" sobre el patron {@code /{id}} sin importar el orden de declaracion.
     *
     * Cada punta acepta UNA de tres formas, en este orden de prioridad: {@code originPlaceId} (lugar
     * canonico de {@code GET /api/places}), {@code originLat}+{@code originLng} (GPS o punto del mapa)
     * y {@code originNeighborhood} (legado: se resuelve por nombre o alias al lugar canonico). Igual
     * con {@code destination...}.
     */
    @Operation(summary = "Buscar ruta",
            description = "Sugiere la mejor ruta directa (sin transbordos) entre un origen y un destino. Cada punta se manda como "
                    + "placeId, lat+lng o nombre (en ese orden de prioridad). Rankea con tiempo propio y cronometra solo la "
                    + "ganadora con Google; si Google falla, etaSource es ESTIMATED.")
    @ApiResponse(responseCode = "200", description = "Ruta sugerida")
    @ApiResponse(responseCode = "400", description = "Una punta no se puede resolver; el detalle dice cual")
    @ApiResponse(responseCode = "404", description = "Ninguna ruta sirve; trae nearestRoutes con la ruta mas cercana a cada punta")
    @GetMapping("/search")
    public RouteSearchResponse search(@RequestParam(required = false) Long originPlaceId,
                                      @RequestParam(required = false) Double originLat,
                                      @RequestParam(required = false) Double originLng,
                                      @RequestParam(required = false) String originNeighborhood,
                                      @RequestParam(required = false) Long destinationPlaceId,
                                      @RequestParam(required = false) Double destinationLat,
                                      @RequestParam(required = false) Double destinationLng,
                                      @RequestParam(required = false) String destinationNeighborhood,
                                      @AuthenticationPrincipal Jwt jwt) {
        RouteSearchResult result = searchRouteUseCase.findBestRoute(
                pointOf("origin", originPlaceId, originLat, originLng, originNeighborhood),
                pointOf("destination", destinationPlaceId, destinationLat, destinationLng, destinationNeighborhood));
        recordIfAuthenticated(jwt, result);
        return RouteSearchResponse.from(result);
    }

    /**
     * Busqueda sin conexion (RF-04): la app llama aqui cuando no tiene red. No consulta a Google, asi
     * que {@code etaSource} siempre es {@code ESTIMATED}; usa el mismo algoritmo geometrico y acepta las
     * mismas tres formas de punta.
     */
    @Operation(summary = "Buscar ruta sin conexión",
            description = "Misma busqueda por geometria sin consultar Google: el tiempo siempre es estimado.")
    @ApiResponse(responseCode = "200", description = "Ruta sugerida")
    @ApiResponse(responseCode = "400", description = "Una punta no se puede resolver; el detalle dice cual")
    @ApiResponse(responseCode = "404", description = "Ninguna ruta sirve; trae nearestRoutes con la ruta mas cercana a cada punta")
    @GetMapping("/search/offline")
    public RouteSearchResponse searchOffline(@RequestParam(required = false) Long originPlaceId,
                                             @RequestParam(required = false) Double originLat,
                                             @RequestParam(required = false) Double originLng,
                                             @RequestParam(required = false) String originNeighborhood,
                                             @RequestParam(required = false) Long destinationPlaceId,
                                             @RequestParam(required = false) Double destinationLat,
                                             @RequestParam(required = false) Double destinationLng,
                                             @RequestParam(required = false) String destinationNeighborhood,
                                             @AuthenticationPrincipal Jwt jwt) {
        RouteSearchResult result = searchRouteUseCase.findBestRouteOffline(
                pointOf("origin", originPlaceId, originLat, originLng, originNeighborhood),
                pointOf("destination", destinationPlaceId, destinationLat, destinationLng, destinationNeighborhood));
        // El historial no distingue online de offline: para el usuario fue la misma
        // busqueda, y lo que repite despues es el par origen-destino.
        recordIfAuthenticated(jwt, result);
        return RouteSearchResponse.from(result);
    }

    /**
     * Escoge UNA forma de punta con la prioridad lugar, coordenadas, nombre. Una sola de las dos
     * coordenadas no ubica nada, asi que equivale a no mandarla.
     *
     * @throws InvalidSearchPointException si la punta no trae ninguna de las tres formas
     */
    private static SearchPoint pointOf(String side, Long placeId, Double lat, Double lng, String name) {
        if (placeId != null) {
            return new SearchPoint.OfPlace(placeId);
        }
        if (lat != null && lng != null) {
            return new SearchPoint.OfCoordinate(new Coordinate(lat, lng));
        }
        if (name != null && !name.isBlank()) {
            return new SearchPoint.OfName(name);
        }
        throw new InvalidSearchPointException(side,
                "falta el punto: manda " + side + "PlaceId, " + side + "Lat+" + side + "Lng o " + side + "Neighborhood");
    }

    @Operation(summary = "Detalle de ruta",
            description = "Retorna la información detallada de una ruta específica mediante su ID, "
                    + "incluido su trazado como LineString de GeoJSON para pintarlo en el mapa. "
                    + "El trazado va en null mientras la ruta no lo tenga cargado.")
    @ApiResponse(responseCode = "200", description = "Detalle de la ruta recuperado exitosamente")
    @ApiResponse(responseCode = "404", description = "La ruta especificada no existe")
    @GetMapping("/{id}")
    public ResponseEntity<RouteDetailResponse> detail(@PathVariable Long id) {
        Route route = getRouteDetailUseCase.getById(id);
        // El aviso se evalua sobre TODOS los barrios de la ruta: en el detalle se ve el
        // recorrido completo, asi que aplica cualquier sector que la ruta atraviese. En
        // una busqueda, en cambio, solo cuentan los barrios entre origen y destino.
        return ResponseEntity.ok().cacheControl(DETAIL_CACHE).body(RouteDetailResponse.from(route,
                getCongestionWarningUseCase.forRoute(route.neighborhoods(), route.path())));
    }

    @Operation(summary = "Costo del pasaje de una ruta",
            description = "Retorna solo las tarifas vigentes de una ruta, de menor a mayor precio, "
                    + "y la fecha en que se actualizó por última vez alguna de ellas. "
                    + "La ruta urbana trae una sola tarifa con referencePoint en null.")
    @ApiResponse(responseCode = "200", description = "Costo del pasaje recuperado exitosamente")
    @ApiResponse(responseCode = "404", description = "La ruta especificada no existe")
    @GetMapping("/{id}/fare")
    public RouteFareResponse fare(@PathVariable Long id) {
        return RouteFareResponse.from(getRouteFareUseCase.getByRouteId(id));
    }
}
