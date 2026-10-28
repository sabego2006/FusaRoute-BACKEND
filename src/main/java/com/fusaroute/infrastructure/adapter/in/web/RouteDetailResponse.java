package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Route;

import java.util.List;

/**
 * Detalle de una ruta (GET /api/routes/{id}): lo mismo que trae el listado mas el
 * trazado para pintarlo en el mapa.
 *
 * Es un tipo aparte de {@link RouteResponse} a proposito: el trazado de una ruta son
 * entre 50 y 80 vertices, y mandarlo en el listado de las 9 rutas multiplicaria el peso
 * de una pantalla que ni siquiera dibuja mapas. El compromiso de rendimiento del
 * frontend es LCP < 2.5 s en 4G lento, asi que el trazado viaja solo cuando se abre la
 * ruta que se va a ver.
 *
 * @param path {@code null} si la ruta todavia no tiene trazado cargado
 */
public record RouteDetailResponse(Long id, String name, String type, List<String> neighborhoods,
                                  List<FareResponse> fares, GeoJsonLineStringResponse path) {

    static RouteDetailResponse from(Route route) {
        return new RouteDetailResponse(
                route.id(),
                route.name(),
                route.type().name(),
                route.neighborhoods(),
                route.fares().stream().map(FareResponse::from).toList(),
                GeoJsonLineStringResponse.from(route.path()));
    }
}
