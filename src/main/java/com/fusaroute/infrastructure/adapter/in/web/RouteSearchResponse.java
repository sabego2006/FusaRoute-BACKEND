package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.ResolvedPoint;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.TrafficSector;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado de GET /api/routes/search y de su variante /search/offline: la ruta
 * sugerida y la ultima milla en cada punta.
 *
 * El letrero de la ruta sugerida viaja dentro de {@code route.signFile} (SCRUM-184).
 *
 * @param estimatedMinutes tiempo en buseta, en minutos enteros. Desde P1 siempre hay numero cuando la
 *     ruta tiene trazado: es el de Google si respondio ({@code etaSource=TRAFFIC}) o el estimado propio
 *     ({@code ESTIMATED}). Solo queda {@code null} en una ruta sin trazado (se encontro por nombre y no
 *     hay con que estimarlo)
 * @param etaSource {@code "TRAFFIC"} o {@code "ESTIMATED"}; en /search/offline siempre {@code ESTIMATED}
 * @param travelDistanceInMeters recorrido en buseta entre el abordaje y la bajada; {@code null} si la
 *     ruta no tiene trazado cargado
 * @param boarding donde tomar la buseta; {@code null} sin trazado. {@code distanceInMeters} ya incluye el
 *     factor de rodeo x1,3 (es la caminata aproximada, no la linea recta)
 * @param dropOff donde bajarse; {@code null} por la misma razon
 * @param longWalk {@code true} si ninguna ruta cumplio el umbral de 800 m y esta es la mejor con hasta
 *     1.500 m de caminata
 * @param congestionSectors sectores con congestion en su franja que el tramo en buseta atraviesa
 * @param origin punta de origen ya resuelta
 * @param destination punta de destino ya resuelta
 * @param path trazado de la ruta sugerida como LineString de GeoJSON; {@code null} si la ruta todavia no
 *     tiene trazado cargado
 * @param lastUpdatedAt el {@code validFrom} mas reciente entre las tarifas vigentes de la ruta sugerida
 *     (RF-06); {@code null} si no tiene ninguna
 */
public record RouteSearchResponse(RouteResponse route, Long estimatedMinutes, String etaSource,
                                  Long travelDistanceInMeters,
                                  BoardingPointResponse boarding, BoardingPointResponse dropOff,
                                  boolean longWalk, List<CongestionSectorResponse> congestionSectors,
                                  SearchPointResponse origin, SearchPointResponse destination,
                                  GeoJsonLineStringResponse path, LocalDate lastUpdatedAt) {

    /** Punta resuelta: donde cayo y como se llama. */
    public record SearchPointResponse(Double lat, Double lng, String label) {

        static SearchPointResponse from(ResolvedPoint point) {
            if (point == null) {
                return null;
            }
            return new SearchPointResponse(
                    point.coordinate() == null ? null : point.coordinate().latitude(),
                    point.coordinate() == null ? null : point.coordinate().longitude(),
                    point.label());
        }
    }

    static RouteSearchResponse from(RouteSearchResult result) {
        return new RouteSearchResponse(
                RouteResponse.from(result.route()),
                result.travelTime().map(Duration::toMinutes).orElse(null),
                result.etaSource().name(),
                result.travelDistance().isPresent() ? Math.round(result.travelDistance().getAsDouble()) : null,
                result.boarding().map(BoardingPointResponse::from).orElse(null),
                result.dropOff().map(BoardingPointResponse::from).orElse(null),
                result.longWalk(),
                result.congestionSectors().stream().map(RouteSearchResponse::sector).toList(),
                SearchPointResponse.from(result.origin()),
                SearchPointResponse.from(result.destination()),
                GeoJsonLineStringResponse.from(result.route().path()),
                result.route().lastFareUpdate().orElse(null));
    }

    private static CongestionSectorResponse sector(TrafficSector sector) {
        return new CongestionSectorResponse(sector.name(), sector.startTime(), sector.endTime());
    }
}
