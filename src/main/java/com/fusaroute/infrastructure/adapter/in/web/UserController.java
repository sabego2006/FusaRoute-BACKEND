package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.FavoriteDestination;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.ChangePasswordCommand;
import com.fusaroute.domain.port.in.ChangePasswordUseCase;
import com.fusaroute.domain.port.in.ClearFavoriteDestinationUseCase;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetFavoriteDestinationUseCase;
import com.fusaroute.domain.port.in.GetProfileUseCase;
import com.fusaroute.domain.port.in.GetSearchHistoryUseCase;
import com.fusaroute.domain.port.in.SetFavoriteDestinationUseCase;
import com.fusaroute.domain.port.in.UpdateProfileCommand;
import com.fusaroute.domain.port.in.UpdateProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints del perfil de usuario (RF-03, SCRUM-14):
 * <ul>
 *   <li>GET    /api/users/me/history              → ultimas busquedas (RF-07, SCRUM-20)</li>
 *   <li>GET    /api/users/me                     → datos del perfil</li>
 *   <li>PUT    /api/users/me                     → actualizar nombre, correo y telefono</li>
 *   <li>PUT    /api/users/me/password            → cambiar contrasena (exige la actual)</li>
 *   <li>GET    /api/users/me/favorite-destination → destino favorito (RF-10, SCRUM-21)</li>
 *   <li>PUT    /api/users/me/favorite-destination → marcar favorito, reemplazando el anterior</li>
 *   <li>DELETE /api/users/me/favorite-destination → quitar el favorito</li>
 * </ul>
 * El id del usuario sale del claim {@code sub} del JWT.
 */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final GetProfileUseCase getProfile;
    private final UpdateProfileUseCase updateProfile;
    private final ChangePasswordUseCase changePassword;
    private final GetSearchHistoryUseCase getSearchHistory;
    private final GetFavoriteDestinationUseCase getFavoriteDestination;
    private final SetFavoriteDestinationUseCase setFavoriteDestination;
    private final ClearFavoriteDestinationUseCase clearFavoriteDestination;
    private final GetCongestionWarningUseCase getCongestionWarning;

    public UserController(GetProfileUseCase getProfile,
                          UpdateProfileUseCase updateProfile,
                          ChangePasswordUseCase changePassword,
                          GetSearchHistoryUseCase getSearchHistory,
                          GetFavoriteDestinationUseCase getFavoriteDestination,
                          SetFavoriteDestinationUseCase setFavoriteDestination,
                          ClearFavoriteDestinationUseCase clearFavoriteDestination,
                          GetCongestionWarningUseCase getCongestionWarning) {
        this.getProfile = getProfile;
        this.updateProfile = updateProfile;
        this.changePassword = changePassword;
        this.getSearchHistory = getSearchHistory;
        this.getFavoriteDestination = getFavoriteDestination;
        this.setFavoriteDestination = setFavoriteDestination;
        this.clearFavoriteDestination = clearFavoriteDestination;
        this.getCongestionWarning = getCongestionWarning;
    }

    /**
     * El aviso de tranco del favorito se evalua sobre su barrio y no sobre una ruta:
     * antes de buscar todavia no hay origen, asi que no hay recorrido que mirar. Es lo
     * unico que se puede responder "sin necesidad de iniciar una busqueda", que es lo
     * que pide el criterio de aceptacion.
     */
    private FavoriteDestinationResponse withCongestion(FavoriteDestination favorite) {
        return FavoriteDestinationResponse.from(favorite,
                getCongestionWarning.forRoute(List.of(favorite.destination())));
    }

    @Operation(summary = "Destino favorito",
            description = "Destino favorito del usuario junto con el aviso de congestión habitual "
                    + "de ese barrio a la hora actual (RF-10). Responde 204 si no tiene ninguno marcado.")
    @ApiResponse(responseCode = "200", description = "Favorito recuperado exitosamente")
    @ApiResponse(responseCode = "204", description = "El usuario no tiene destino favorito")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping("/favorite-destination")
    public ResponseEntity<FavoriteDestinationResponse> favoriteDestination(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        // 204 y no 404: no tener favorito es un estado normal de la cuenta, no un
        // recurso que falte. Un 404 obligaria al frontend a tratar como error algo que
        // le pasa a todo usuario nuevo.
        return getFavoriteDestination.of(userId)
                .map(favorite -> ResponseEntity.ok(withCongestion(favorite)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Marcar destino favorito",
            description = "Deja el destino como favorito del usuario. Solo hay uno activo a la vez: "
                    + "marcar uno nuevo reemplaza al anterior (RF-10).")
    @ApiResponse(responseCode = "200", description = "Favorito guardado exitosamente")
    @ApiResponse(responseCode = "400", description = "Destino vacío o demasiado largo")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @PutMapping("/favorite-destination")
    public FavoriteDestinationResponse setFavoriteDestination(@AuthenticationPrincipal Jwt jwt,
                                                              @RequestBody FavoriteDestinationRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        // Se devuelve con el aviso ya calculado para que marcar un favorito deje la
        // pantalla en el mismo estado que tendria al recargarla, sin un GET extra.
        return withCongestion(setFavoriteDestination.set(userId, request.destination()));
    }

    @Operation(summary = "Quitar destino favorito",
            description = "Deja al usuario sin destino favorito (RF-10). Es idempotente.")
    @ApiResponse(responseCode = "204", description = "Favorito quitado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @DeleteMapping("/favorite-destination")
    public ResponseEntity<Void> clearFavoriteDestination(@AuthenticationPrincipal Jwt jwt) {
        clearFavoriteDestination.clear(Long.parseLong(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Historial de búsquedas",
            description = "Últimas búsquedas del usuario, de la más reciente a la más antigua (RF-07)")
    @ApiResponse(responseCode = "200", description = "Historial recuperado exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping("/history")
    public List<SearchHistoryResponse> searchHistory(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return getSearchHistory.historyOf(userId).stream().map(SearchHistoryResponse::from).toList();
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
