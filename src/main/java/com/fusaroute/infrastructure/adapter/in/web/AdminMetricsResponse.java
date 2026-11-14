package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.AdminMetrics;
import com.fusaroute.domain.model.TopRoute;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cuerpo de {@code GET /api/admin/metrics} (RF-13, SCRUM-26): las cinco metricas del panel y tres campos de
 * metadatos. Sin datos personales: solo conteos y nombres de ruta.
 */
public record AdminMetricsResponse(
        @Schema(description = "Cuentas registradas, de cualquier rol") long totalUsers,
        @Schema(description = "Busquedas del dia calendario actual en America/Bogota "
                + "(00:00 inclusive a 24:00 exclusive)") long searchesToday,
        @Schema(description = "Hasta 5 rutas mas consultadas, por busquedas desc y luego por nombre; "
                + "solo los ultimos topRoutesWindowDays dias") List<TopRouteResponse> topRoutes,
        @Schema(description = "Comentarios pendientes de respuesta") long pendingComments,
        @Schema(description = "Suspensiones efectivamente vigentes ahora") long activeSuspensions,
        @Schema(description = "Instante del calculo, ISO-8601 con offset") OffsetDateTime generatedAt,
        @Schema(description = "Zona con la que se decide el dia de hoy", example = "America/Bogota") String timezone,
        @Schema(description = "Dias que mira el ranking de rutas", example = "30") int topRoutesWindowDays) {

    public static AdminMetricsResponse from(AdminMetrics metrics) {
        return new AdminMetricsResponse(
                metrics.totalUsers(),
                metrics.searchesToday(),
                metrics.topRoutes().stream().map(TopRouteResponse::from).toList(),
                metrics.pendingComments(),
                metrics.activeSuspensions(),
                metrics.generatedAt(),
                metrics.timezone(),
                metrics.topRoutesWindowDays());
    }

    /** Una fila del ranking. */
    public record TopRouteResponse(Long routeId, String routeName, long searches) {

        static TopRouteResponse from(TopRoute route) {
            return new TopRouteResponse(route.routeId(), route.routeName(), route.searches());
        }
    }
}
