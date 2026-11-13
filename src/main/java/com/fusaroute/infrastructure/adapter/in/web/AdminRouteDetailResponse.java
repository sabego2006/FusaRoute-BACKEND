package com.fusaroute.infrastructure.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Detalle de una ruta para el administrador: el resumen mas trazado, barrios en orden y tarifas vigentes. */
public record AdminRouteDetailResponse(Long id, String name, RouteType type, RouteStatus status,
                                       boolean hasPath, int neighborhoodsCount, BigDecimal currentFare,
                                       Instant updatedAt, JsonNode pathGeoJson, List<String> neighborhoods,
                                       List<FareItem> fares, String signFile) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record FareItem(BigDecimal amount, String referencePoint, LocalDate validFrom) {
    }

    public static AdminRouteDetailResponse from(AdminRoute route) {
        return new AdminRouteDetailResponse(route.id(), route.name(), route.type(), route.status(),
                route.hasPath(), route.neighborhoods().size(), route.currentFare().orElse(null),
                route.updatedAt(), parse(route.pathGeoJson()), route.neighborhoods(),
                route.fares().stream()
                        .map(f -> new FareItem(f.amount(), f.referencePoint(), f.validFrom())).toList(),
                route.signFile());
    }

    private static JsonNode parse(String geoJson) {
        if (geoJson == null) {
            return null;
        }
        try {
            return JSON.readTree(geoJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("path_geojson invalido en la base de datos", e);
        }
    }
}
