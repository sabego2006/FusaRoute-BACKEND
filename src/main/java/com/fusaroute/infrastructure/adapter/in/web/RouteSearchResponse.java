package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.RouteSearchResult;

/**
 * Resultado de GET /api/routes/search: la ruta sugerida, el tiempo estimado para
 * recorrerla y la ultima milla en cada punta.
 *
 * @param boarding donde tomar la buseta; {@code null} si la ruta no tiene trazado
 *     cargado todavia, caso en el que igual se sugiere el viaje
 * @param dropOff donde bajarse; {@code null} por la misma razon
 */
public record RouteSearchResponse(RouteResponse route, long estimatedMinutes,
                                  BoardingPointResponse boarding, BoardingPointResponse dropOff) {

    static RouteSearchResponse from(RouteSearchResult result) {
        return new RouteSearchResponse(
                RouteResponse.from(result.route()),
                result.estimatedTime().toMinutes(),
                result.boarding().map(BoardingPointResponse::from).orElse(null),
                result.dropOff().map(BoardingPointResponse::from).orElse(null));
    }
}
