package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.FareDraft;
import com.fusaroute.domain.model.RouteDraft;
import com.fusaroute.domain.model.RouteType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cuerpo de POST y PUT /api/admin/routes. Solo traduce JSON a {@link RouteDraft}; las
 * reglas de validacion viven en el dominio. El PUT reemplaza la ruta completa: un
 * {@code pathGeoJson} ausente deja la ruta sin trazado.
 */
public record AdminRouteRequest(String name, RouteType type, GeoJsonLineStringRequest pathGeoJson,
                                List<String> neighborhoods, List<FareRequest> fares, String signFile) {

    /** LineString de GeoJSON: {@code {"type":"LineString","coordinates":[[lon,lat],...]}}. */
    public record GeoJsonLineStringRequest(String type, List<List<Double>> coordinates) {
    }

    public record FareRequest(BigDecimal amount, String referencePoint) {
    }

    public RouteDraft toDraft() {
        return new RouteDraft(name, type,
                pathGeoJson == null ? null : pathGeoJson.type(),
                pathGeoJson == null ? null : pathGeoJson.coordinates(),
                neighborhoods,
                fares == null ? null : fares.stream()
                        .map(f -> f == null ? null : new FareDraft(f.amount(), f.referencePoint())).toList(),
                signFile);
    }
}
