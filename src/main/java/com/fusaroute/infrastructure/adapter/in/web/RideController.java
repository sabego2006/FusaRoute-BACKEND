package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.GetRideHistoryUseCase;
import com.fusaroute.domain.port.in.RecordRideCommand;
import com.fusaroute.domain.port.in.RecordRideUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "Ya me subi" y "Tus ultimos viajes". Controlador aparte de {@link UserController}
 * para no seguir engordando su constructor; la ruta sigue bajo /api/users/me, asi que
 * la protege la misma regla de SecurityConfig.
 */
@RestController
@RequestMapping("/api/users/me/rides")
public class RideController {

    private final RecordRideUseCase recordRide;
    private final GetRideHistoryUseCase getRideHistory;

    public RideController(RecordRideUseCase recordRide, GetRideHistoryUseCase getRideHistory) {
        this.recordRide = recordRide;
        this.getRideHistory = getRideHistory;
    }

    @Operation(summary = "Ya me subí",
            description = "Guarda el viaje tomado. Se conservan los 10 más recientes por usuario.")
    @ApiResponse(responseCode = "201", description = "Viaje guardado")
    @ApiResponse(responseCode = "400", description = "Datos del viaje inválidos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Ruta inexistente o suspendida")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RideResponse board(@AuthenticationPrincipal Jwt jwt, @RequestBody RideRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return RideResponse.from(recordRide.record(new RecordRideCommand(userId, request.routeId(),
                request.destinationLabel(), request.destinationPlaceId(),
                request.destinationLat(), request.destinationLng(), request.originLabel())));
    }

    @Operation(summary = "Tus últimos viajes",
            description = "Los 10 viajes más recientes, del más nuevo al más viejo. El primero es el último destino.")
    @ApiResponse(responseCode = "200", description = "Historial recuperado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping
    public List<RideResponse> rides(@AuthenticationPrincipal Jwt jwt) {
        return getRideHistory.ridesOf(Long.parseLong(jwt.getSubject())).stream()
                .map(RideResponse::from).toList();
    }
}
