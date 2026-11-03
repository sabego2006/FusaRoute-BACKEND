package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.ConfirmTotpCommand;
import com.fusaroute.domain.port.in.ConfirmTotpResult;
import com.fusaroute.domain.port.in.ConfirmTotpUseCase;
import com.fusaroute.domain.port.in.EnrollTotpCommand;
import com.fusaroute.domain.port.in.EnrollTotpUseCase;
import com.fusaroute.domain.port.in.GetTotpStatusUseCase;
import com.fusaroute.domain.port.in.RegenerateRecoveryCodesUseCase;
import com.fusaroute.domain.port.in.TotpEnrollmentResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del segundo factor TOTP del usuario autenticado (SCRUM-179):
 * <ul>
 *   <li>{@code GET  /api/users/me/totp}         — estado (enabled: boolean);</li>
 *   <li>{@code POST /api/users/me/totp/enroll}  — genera secreto + QR + otpauth://;</li>
 *   <li>{@code POST /api/users/me/totp/confirm} — valida primer codigo, activa TOTP,
 *       devuelve codigos de respaldo + JWT full de 7 dias.</li>
 * </ul>
 * El id del usuario sale del claim {@code sub} del JWT, igual que en
 * {@link UserController}. Los dos endpoints de enrolamiento aceptan tanto el
 * JWT completo (re-enrolamiento desde perfil) como el token de enrolamiento
 * ({@code scope=totp_enroll}); el filtro de alcance lo gobierna
 * {@link com.fusaroute.infrastructure.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/users/me/totp")
public class TotpController {

    private final GetTotpStatusUseCase getStatus;
    private final EnrollTotpUseCase enroll;
    private final ConfirmTotpUseCase confirm;
    private final RegenerateRecoveryCodesUseCase regenerateRecoveryCodes;

    public TotpController(GetTotpStatusUseCase getStatus,
                          EnrollTotpUseCase enroll,
                          ConfirmTotpUseCase confirm,
                          RegenerateRecoveryCodesUseCase regenerateRecoveryCodes) {
        this.getStatus = getStatus;
        this.enroll = enroll;
        this.confirm = confirm;
        this.regenerateRecoveryCodes = regenerateRecoveryCodes;
    }

    @Operation(summary = "Estado del segundo factor",
            description = "Indica si el usuario autenticado ya confirmo su app autenticadora")
    @ApiResponse(responseCode = "200", description = "Estado consultado")
    @GetMapping
    public TotpStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return new TotpStatusResponse(getStatus.isEnabled(userId));
    }

    @Operation(summary = "Iniciar el enrolamiento del segundo factor",
            description = "Genera un secreto nuevo y devuelve el URI otpauth:// y el QR en PNG Base64. "
                    + "Acepta el token completo (re-enrolamiento por cambio de telefono, que apaga el "
                    + "factor anterior) o el token de enrolamiento (primer enrolamiento).")
    @ApiResponse(responseCode = "200", description = "Secreto y QR generados")
    @ApiResponse(responseCode = "409", description = "Ya tiene el segundo factor activo y el token no permite resetearlo")
    @PostMapping("/enroll")
    public TotpEnrollResponse enroll(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        TotpEnrollmentResult result = enroll.enroll(new EnrollTotpCommand(userId, hasFullScope(jwt)));
        return TotpEnrollResponse.from(result);
    }

    @Operation(summary = "Confirmar el enrolamiento del segundo factor",
            description = "Valida el primer codigo, activa el segundo factor y deja al usuario logueado: "
                    + "devuelve el JWT completo de 7 dias y los 10 codigos de respaldo, que se muestran una sola vez.")
    @ApiResponse(responseCode = "200", description = "Segundo factor activado")
    @ApiResponse(responseCode = "401", description = "Codigo invalido o sin enrolamiento en curso")
    @ApiResponse(responseCode = "409", description = "El segundo factor ya estaba activo")
    @PostMapping("/confirm")
    public ConfirmTotpResponse confirm(@AuthenticationPrincipal Jwt jwt,
                                        @RequestBody ConfirmTotpRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        ConfirmTotpResult result = confirm.confirm(new ConfirmTotpCommand(userId, request.code()));
        return ConfirmTotpResponse.from(result);
    }

    @Operation(summary = "Regenerar los codigos de respaldo",
            description = "Reemplaza los 10 codigos de respaldo por un conjunto nuevo. "
                    + "Los anteriores dejan de servir y los nuevos se muestran una sola vez.")
    @ApiResponse(responseCode = "200", description = "Codigos nuevos emitidos")
    @ApiResponse(responseCode = "409", description = "El segundo factor no esta activo")
    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse regenerateRecoveryCodes(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return RecoveryCodesResponse.from(regenerateRecoveryCodes.regenerate(userId));
    }

    /**
     * Solo un token completo puede resetear un segundo factor ya activo. Un
     * token sin el claim {@code scope} cuenta como completo, igual que en
     * {@link com.fusaroute.infrastructure.config.SecurityConfig}: son los
     * emitidos antes de SCRUM-179, aun vigentes.
     */
    private static boolean hasFullScope(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        return scope == null || scope.isBlank() || "full".equals(scope.trim());
    }
}
