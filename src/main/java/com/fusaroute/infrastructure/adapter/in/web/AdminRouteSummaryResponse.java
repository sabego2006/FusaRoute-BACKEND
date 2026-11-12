package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;

import java.math.BigDecimal;
import java.time.Instant;

/** Fila del listado del administrador. {@code currentFare} es la menor tarifa vigente (nula si no hay). */
public record AdminRouteSummaryResponse(Long id, String name, RouteType type, RouteStatus status,
                                        boolean hasPath, int neighborhoodsCount, BigDecimal currentFare,
                                        Instant updatedAt) {

    public static AdminRouteSummaryResponse from(AdminRoute route) {
        return new AdminRouteSummaryResponse(route.id(), route.name(), route.type(), route.status(),
                route.hasPath(), route.neighborhoods().size(), route.currentFare().orElse(null),
                route.updatedAt());
    }
}
