package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.LogLevel;
import com.fusaroute.domain.port.in.ListAppLogsUseCase;
import com.fusaroute.domain.port.in.ListUsageEventsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Consulta de los logs y eventos de uso guardados en la base (SCRUM-186):
 * <ul>
 *   <li>GET /api/admin/logs?level=&amp;since=&amp;limit=</li>
 *   <li>GET /api/admin/search-events?since=&amp;limit=</li>
 * </ul>
 *
 * <p>Como en {@link AdminCommentController}, este controlador no comprueba el rol:
 * {@code SecurityConfig} exige ADMIN y scope completo sobre todo {@code /api/admin/**}.
 *
 * <p>{@code since} va en ISO-8601 (por ejemplo {@code 2026-11-08T00:00:00Z}); {@code level}
 * es exacto (INFO, WARN o ERROR), no "este nivel o superior".
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMonitoringController {

    private final ListAppLogsUseCase listAppLogs;
    private final ListUsageEventsUseCase listUsageEvents;

    public AdminMonitoringController(ListAppLogsUseCase listAppLogs, ListUsageEventsUseCase listUsageEvents) {
        this.listAppLogs = listAppLogs;
        this.listUsageEvents = listUsageEvents;
    }

    @Operation(summary = "Logs de la aplicación (administrador)",
            description = "WARN y ERROR de la app más la fila INFO de cada arranque, del más reciente "
                    + "al más antiguo. level (INFO, WARN, ERROR) filtra por nivel exacto; since "
                    + "(ISO-8601) filtra desde esa fecha; limit va de 1 a 500 (por defecto 100).")
    @ApiResponse(responseCode = "200", description = "Logs recuperados exitosamente")
    @ApiResponse(responseCode = "400", description = "level, since o limit no son válidos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @GetMapping("/logs")
    public List<AppLogResponse> logs(@RequestParam(required = false) LogLevel level,
                                     @RequestParam(required = false) Instant since,
                                     @RequestParam(required = false) Integer limit) {
        return listAppLogs.list(level, since, limit).stream().map(AppLogResponse::from).toList();
    }

    @Operation(summary = "Eventos de uso (administrador)",
            description = "Una fila por búsqueda de ruta y por toque de «Ya me subí», del más reciente "
                    + "al más antiguo. Sin IP ni usuario. since (ISO-8601) filtra desde esa fecha; "
                    + "limit va de 1 a 500 (por defecto 100).")
    @ApiResponse(responseCode = "200", description = "Eventos recuperados exitosamente")
    @ApiResponse(responseCode = "400", description = "since o limit no son válidos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @GetMapping("/search-events")
    public List<UsageEventResponse> searchEvents(@RequestParam(required = false) Instant since,
                                                 @RequestParam(required = false) Integer limit) {
        return listUsageEvents.list(since, limit).stream().map(UsageEventResponse::from).toList();
    }
}
