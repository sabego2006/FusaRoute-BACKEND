package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.RouteSearchResult;

import java.time.Duration;

/**
 * Resultado de GET /api/routes/search y de su variante /search/offline: la ruta
 * sugerida y la ultima milla en cada punta.
 *
 * @param estimatedMinutes tiempo en buseta; {@code null} en la busqueda offline, que
 *     no consulta a Google y por tanto no tiene con que estimarlo
 * @param travelDistanceInMeters recorrido en buseta entre el abordaje y la bajada;
 *     {@code null} si la ruta no tiene trazado cargado
 * @param boarding donde tomar la buseta; {@code null} si la ruta no tiene trazado
 *     cargado todavia, caso en el que igual se sugiere el viaje
 * @param dropOff donde bajarse; {@code null} por la misma razon
 */
public record RouteSearchResponse(RouteResponse route, Long estimatedMinutes, Long travelDistanceInMeters,
                                  BoardingPointResponse boarding, BoardingPointResponse dropOff) {

    static RouteSearchResponse from(RouteSearchResult result) {
        return new RouteSearchResponse(
                RouteResponse.from(result.route()),
                result.travelTime().map(Duration::toMinutes).orElse(null),
                result.travelDistance().isPresent() ? Math.round(result.travelDistance().getAsDouble()) : null,
                result.boarding().map(BoardingPointResponse::from).orElse(null),
                result.dropOff().map(BoardingPointResponse::from).orElse(null));
    }
}
