package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.ConfirmTotpCommand;
import com.fusaroute.domain.port.in.ConfirmTotpResult;
import com.fusaroute.domain.port.in.ConfirmTotpUseCase;
import com.fusaroute.domain.port.in.EnrollTotpUseCase;
import com.fusaroute.domain.port.in.GetTotpStatusUseCase;
import com.fusaroute.domain.port.in.TotpEnrollmentResult;
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

    public TotpController(GetTotpStatusUseCase getStatus,
                          EnrollTotpUseCase enroll,
                          ConfirmTotpUseCase confirm) {
        this.getStatus = getStatus;
        this.enroll = enroll;
        this.confirm = confirm;
    }

    @GetMapping
    public TotpStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return new TotpStatusResponse(getStatus.isEnabled(userId));
    }

    @PostMapping("/enroll")
    public TotpEnrollResponse enroll(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        TotpEnrollmentResult result = enroll.enroll(userId);
        return TotpEnrollResponse.from(result);
    }

    @PostMapping("/confirm")
    public ConfirmTotpResponse confirm(@AuthenticationPrincipal Jwt jwt,
                                        @RequestBody ConfirmTotpRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        ConfirmTotpResult result = confirm.confirm(new ConfirmTotpCommand(userId, request.code()));
        return ConfirmTotpResponse.from(result);
    }
}
