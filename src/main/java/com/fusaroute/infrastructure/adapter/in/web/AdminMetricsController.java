package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.GetAdminMetricsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de metricas de uso del administrador (RF-13, SCRUM-26): {@code GET /api/admin/metrics}.
 *
 * <p>Como {@link AdminCommentController}, no comprueba el rol: {@code SecurityConfig} exige ROLE_ADMIN y
 * scope completo sobre todo {@code /api/admin/**}, y eso cubre el 401 y el 403 (SCRUM-124).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMetricsController {

    private final GetAdminMetricsUseCase getAdminMetrics;

    public AdminMetricsController(GetAdminMetricsUseCase getAdminMetrics) {
        this.getAdminMetrics = getAdminMetrics;
    }

    @Operation(summary = "Panel de métricas de uso (administrador)",
            description = "Exactamente cinco métricas calculadas al pedirse, sin caché: usuarios registrados, "
                    + "búsquedas del día actual en hora de Colombia, las 5 rutas más consultadas (de los "
                    + "últimos 30 días: search_events se purga a 30 días, no hay historial acumulado), "
                    + "comentarios pendientes de respuesta y suspensiones vigentes ahora.")
    @ApiResponse(responseCode = "200", description = "Métricas calculadas exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @GetMapping("/metrics")
    public AdminMetricsResponse metrics() {
        return AdminMetricsResponse.from(getAdminMetrics.getMetrics());
    }
}
