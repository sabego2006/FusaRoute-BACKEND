package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSign;

import java.time.LocalDate;
import java.util.List;

/**
 * Detalle de una ruta (GET /api/routes/{id}): lo mismo que trae el listado, mas el
 * trazado para pintarlo en el mapa y los sectores con congestion que la afectan ahora.
 *
 * Es un tipo aparte de {@link RouteResponse} a proposito: el trazado de una ruta son
 * entre 50 y 80 vertices, y mandarlo en el listado de las 9 rutas multiplicaria el peso
 * de una pantalla que ni siquiera dibuja mapas. El compromiso de rendimiento del
 * frontend es LCP < 2.5 s en 4G lento, asi que el trazado viaja solo cuando se abre la
 * ruta que se va a ver.
 *
 * @param lastUpdatedAt el {@code validFrom} mas reciente entre las tarifas vigentes (RF-06);
 *     {@code null} si la ruta no tiene ninguna. En una intermunicipal es la fecha del precio
 *     que mas recientemente cambio, no la de todos: cada fila de {@code fares} trae la suya
 * @param path {@code null} si la ruta todavia no tiene trazado cargado
 * @param congestionSectors sectores que el recorrido atraviesa y estan en su franja de
 *     trancon en este momento (RF-09); lista vacia si ninguno
 * @param signs letreros fisicos de la ruta (SCRUM-176); lista vacia si todavia no hay
 *     evidencia de campo que la respalde — no se inventa un letrero para rellenarla
 */
public record RouteDetailResponse(Long id, String name, String type, List<String> neighborhoods,
                                  List<FareResponse> fares, LocalDate lastUpdatedAt,
                                  GeoJsonLineStringResponse path,
                                  List<CongestionSectorResponse> congestionSectors,
                                  List<RouteSignResponse> signs) {

    static RouteDetailResponse from(Route route, CongestionWarning congestion, List<RouteSign> signs) {
        return new RouteDetailResponse(
                route.id(),
                route.name(),
                route.type().name(),
                route.neighborhoods(),
                route.fares().stream().map(FareResponse::from).toList(),
                route.lastFareUpdate().orElse(null),
                GeoJsonLineStringResponse.from(route.path()),
                CongestionSectorResponse.from(congestion),
                RouteSignResponse.from(signs));
    }
}
