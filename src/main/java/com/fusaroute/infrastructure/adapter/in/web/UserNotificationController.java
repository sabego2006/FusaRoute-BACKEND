package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.GetUserNotificationsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Avisos in-app del usuario (RF-12, SCRUM-25). Exige sesion por el {@code anyRequest()} de
 * {@code SecurityConfig}; el id del usuario sale del claim {@code sub} del JWT.
 */
@RestController
@RequestMapping("/api/users/me/notifications")
public class UserNotificationController {

    private final GetUserNotificationsUseCase getNotifications;

    public UserNotificationController(GetUserNotificationsUseCase getNotifications) {
        this.getNotifications = getNotifications;
    }

    @Operation(summary = "Avisos del usuario",
            description = "Rutas suspendidas ahora mismo que afectan al usuario: la ruta sirve su destino favorito "
                    + "(because = FAVORITE) o está en al menos 3 de sus últimos 6 viajes (because = FREQUENT). "
                    + "Lista vacía si nada aplica. Se calcula al leer; el cliente descarta por sesión.")
    @ApiResponse(responseCode = "200", description = "Avisos recuperados (posiblemente vacíos)")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping
    public List<UserNotificationResponse> notifications(@AuthenticationPrincipal Jwt jwt) {
        return getNotifications.notificationsOf(Long.parseLong(jwt.getSubject())).stream()
                .map(UserNotificationResponse::from).toList();
    }
}
