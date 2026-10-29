package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteSign;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado de GET /api/routes/search y de su variante /search/offline: la ruta
 * sugerida y la ultima milla en cada punta.
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
 *     Pesa (~50 vertices) pero es el mismo dato que el detalle ya expone; el ahorro
 *     que daba separar listado y detalle no aplica aqui, donde la busqueda devuelve
 *     exactamente una ruta
 * @param signs letreros fisicos de la ruta sugerida (SCRUM-176); lista vacia si
 *     todavia no hay evidencia de campo. Va aqui y no dentro de {@code route} porque
 *     {@link RouteResponse} se queda liviano a proposito para el listado publico.
 * @param lastUpdatedAt el {@code validFrom} mas reciente entre las tarifas vigentes de
 *     la ruta sugerida (RF-06); {@code null} si no tiene ninguna. Por la misma razon
 *     que {@code signs}, va aqui y no en {@code route}: el listado publico no lo lleva.
 */
public record RouteSearchResponse(RouteResponse route, Long estimatedMinutes, Long travelDistanceInMeters,
                                  BoardingPointResponse boarding, BoardingPointResponse dropOff,
                                  GeoJsonLineStringResponse path,
                                  List<RouteSignResponse> signs, LocalDate lastUpdatedAt) {

    static RouteSearchResponse from(RouteSearchResult result, List<RouteSign> signs) {
        return new RouteSearchResponse(
                RouteResponse.from(result.route()),
                result.travelTime().map(Duration::toMinutes).orElse(null),
                result.travelDistance().isPresent() ? Math.round(result.travelDistance().getAsDouble()) : null,
                result.boarding().map(BoardingPointResponse::from).orElse(null),
                result.dropOff().map(BoardingPointResponse::from).orElse(null),
                GeoJsonLineStringResponse.from(result.route().path()),
                RouteSignResponse.from(signs),
                result.route().lastFareUpdate().orElse(null));
    }
}
