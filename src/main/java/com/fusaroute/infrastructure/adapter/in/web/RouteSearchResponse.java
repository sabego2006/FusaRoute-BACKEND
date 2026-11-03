package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.RouteSearchResult;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Resultado de GET /api/routes/search y de su variante /search/offline: la ruta
 * sugerida y la ultima milla en cada punta.
 *
 * El letrero de la ruta sugerida viaja dentro de {@code route.signFile} (SCRUM-184).
 * Ya no hay un campo {@code signs} aparte: con un letrero por ruta, el dato cabe en
 * {@link RouteResponse} y no hace falta sacarlo del objeto de la ruta.
 *
 * @param estimatedMinutes tiempo en buseta; {@code null} en la busqueda offline, que
 *     no consulta a Google y por tanto no tiene con que estimarlo. Tambien queda
 *     {@code null} si la busqueda online degrado por fallo del proveedor (ver
 *     {@code SearchRouteService.findBestRoute}): el campo {@code travelDistanceInMeters}
 *     sigue viajando y es el que el cliente muestra en ese caso
 * @param travelDistanceInMeters recorrido en buseta entre el abordaje y la bajada;
 *     {@code null} si la ruta no tiene trazado cargado
 * @param boarding donde tomar la buseta; {@code null} si la ruta no tiene trazado
 *     cargado todavia, caso en el que igual se sugiere el viaje
 * @param dropOff donde bajarse; {@code null} por la misma razon
 * @param path trazado de la ruta sugerida como LineString de GeoJSON; {@code null} si
 *     la ruta todavia no tiene trazado cargado. Permite pintar el mapa en el resultado
 *     de la busqueda sin pedir el detalle completo, como ya hace {@link RouteDetailResponse}.
 *     Pesa, pero es el mismo dato que el detalle ya expone; el ahorro que daba separar
 *     listado y detalle no aplica aqui, donde la busqueda devuelve exactamente una ruta
 * @param lastUpdatedAt el {@code validFrom} mas reciente entre las tarifas vigentes de
 *     la ruta sugerida (RF-06); {@code null} si no tiene ninguna. Va aqui y no en
 *     {@code route} porque el listado publico no lo lleva
 */
public record RouteSearchResponse(RouteResponse route, Long estimatedMinutes, Long travelDistanceInMeters,
                                  BoardingPointResponse boarding, BoardingPointResponse dropOff,
                                  GeoJsonLineStringResponse path, LocalDate lastUpdatedAt) {

    static RouteSearchResponse from(RouteSearchResult result) {
        return new RouteSearchResponse(
                RouteResponse.from(result.route()),
                result.travelTime().map(Duration::toMinutes).orElse(null),
                result.travelDistance().isPresent() ? Math.round(result.travelDistance().getAsDouble()) : null,
                result.boarding().map(BoardingPointResponse::from).orElse(null),
                result.dropOff().map(BoardingPointResponse::from).orElse(null),
                GeoJsonLineStringResponse.from(result.route().path()),
                result.route().lastFareUpdate().orElse(null));
    }
}
