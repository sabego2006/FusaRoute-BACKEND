package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.ChangePasswordCommand;
import com.fusaroute.domain.port.in.ChangePasswordUseCase;
import com.fusaroute.domain.port.in.GetProfileUseCase;
import com.fusaroute.domain.port.in.UpdateProfileCommand;
import com.fusaroute.domain.port.in.UpdateProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del perfil de usuario (RF-03, SCRUM-14):
 * <ul>
 *   <li>GET  /api/users/me           → datos del perfil</li>
 *   <li>PUT  /api/users/me           → actualizar nombre, correo y telefono</li>
 *   <li>PUT  /api/users/me/password  → cambiar contrasena (exige la actual)</li>
 * </ul>
 * El id del usuario sale del claim {@code sub} del JWT.
 */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final GetProfileUseCase getProfile;
    private final UpdateProfileUseCase updateProfile;
    private final ChangePasswordUseCase changePassword;

    public UserController(GetProfileUseCase getProfile,
                          UpdateProfileUseCase updateProfile,
                          ChangePasswordUseCase changePassword) {
        this.getProfile = getProfile;
        this.updateProfile = updateProfile;
        this.changePassword = changePassword;
    }

    @Operation(summary = "Obtener perfil", description = "Retorna la información del usuario autenticado")
    @ApiResponse(responseCode = "200", description = "Perfil recuperado exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        User user = getProfile.getProfile(userId);
        return UserProfileResponse.from(user);
    }

    @Operation(summary = "Actualizar perfil", description = "Actualiza el nombre, correo y teléfono del usuario")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos de actualización inválidos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @PutMapping
    public UserProfileResponse update(@AuthenticationPrincipal Jwt jwt,
                                      @RequestBody UpdateProfileRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        User user = updateProfile.updateProfile(
                new UpdateProfileCommand(userId, request.name(), request.email(), request.phone()));
        return UserProfileResponse.from(user);
    }

    @Operation(summary = "Cambiar contraseña", description = "Cambia la contraseña del usuario exigiendo la contraseña actual")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada exitosamente")
    @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o nueva contraseña inválida")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                @RequestBody ChangePasswordRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        changePassword.changePassword(
                new ChangePasswordCommand(userId, request.currentPassword(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }
}
