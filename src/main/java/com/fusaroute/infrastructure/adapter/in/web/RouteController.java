package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catalogo publico de rutas (RF-15, SCRUM-19) y busqueda de la mejor ruta (RF-04, SCRUM-54). Solo lectura y sin sesion. */
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final ListActiveRoutesUseCase listActiveRoutesUseCase;
    private final GetRouteDetailUseCase getRouteDetailUseCase;
    private final GetCongestionWarningUseCase getCongestionWarningUseCase;
    private final GetRouteFareUseCase getRouteFareUseCase;
    private final SearchRouteUseCase searchRouteUseCase;

    public RouteController(ListActiveRoutesUseCase listActiveRoutesUseCase,
                           GetRouteDetailUseCase getRouteDetailUseCase,
                           GetCongestionWarningUseCase getCongestionWarningUseCase,
                           GetRouteFareUseCase getRouteFareUseCase,
                           SearchRouteUseCase searchRouteUseCase) {
        this.listActiveRoutesUseCase = listActiveRoutesUseCase;
        this.getRouteDetailUseCase = getRouteDetailUseCase;
        this.getCongestionWarningUseCase = getCongestionWarningUseCase;
        this.getRouteFareUseCase = getRouteFareUseCase;
        this.searchRouteUseCase = searchRouteUseCase;
    }

    @Operation(summary = "Listar rutas activas", description = "Retorna la lista de todas las rutas de buseta que se encuentran activas")
    @ApiResponse(responseCode = "200", description = "Lista de rutas recuperada exitosamente")
    @GetMapping
    public List<RouteResponse> list() {
        return listActiveRoutesUseCase.listActive().stream().map(RouteResponse::from).toList();
    }

    /**
     * Busca la mejor ruta entre un origen y un destino. Va antes de {@code /{id}} en
     * este archivo solo por orden de lectura: Spring ya prefiere el segmento literal
     * "search" sobre el patron {@code /{id}} sin importar el orden de declaracion.
     */
    @GetMapping("/search")
    public RouteSearchResponse search(@RequestParam String originNeighborhood,
                                      @RequestParam double originLat,
                                      @RequestParam double originLng,
                                      @RequestParam String destinationNeighborhood,
                                      @RequestParam double destinationLat,
                                      @RequestParam double destinationLng) {
        return RouteSearchResponse.from(searchRouteUseCase.findBestRoute(
                originNeighborhood, new Coordinate(originLat, originLng),
                destinationNeighborhood, new Coordinate(destinationLat, destinationLng)));
    }

    @Operation(summary = "Detalle de ruta",
            description = "Retorna la información detallada de una ruta específica mediante su ID, "
                    + "incluido su trazado como LineString de GeoJSON para pintarlo en el mapa. "
                    + "El trazado va en null mientras la ruta no lo tenga cargado.")
    @ApiResponse(responseCode = "200", description = "Detalle de la ruta recuperado exitosamente")
    @ApiResponse(responseCode = "404", description = "La ruta especificada no existe")
    @GetMapping("/{id}")
    public RouteDetailResponse detail(@PathVariable Long id) {
        Route route = getRouteDetailUseCase.getById(id);
        // El aviso se evalua sobre TODOS los barrios de la ruta: en el detalle se ve el
        // recorrido completo, asi que aplica cualquier sector que la ruta atraviese. En
        // una busqueda, en cambio, solo cuentan los barrios entre origen y destino.
        return RouteDetailResponse.from(route, getCongestionWarningUseCase.forRoute(route.neighborhoods()));
    }

    @GetMapping("/{id}/fare")
    public RouteFareResponse fare(@PathVariable Long id) {
        return RouteFareResponse.from(getRouteFareUseCase.getByRouteId(id));
    }
}
