package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.ConfirmTotpResult;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta de {@code POST /api/users/me/totp/confirm} (SCRUM-179). Confirma el
 * enrolamiento y DEJA LOGUEADO al usuario: trae el JWT completo de 7 dias, igual
 * forma que {@link LoginResponse}, mas los codigos de respaldo en claro que se
 * muestran una sola vez.
 */
public record ConfirmTotpResponse(String token, String tokenType, Instant expiresAt,
                                  RegisterUserResponse user, List<String> recoveryCodes) {

    static ConfirmTotpResponse from(ConfirmTotpResult result) {
        return new ConfirmTotpResponse(
                result.token().value(),
                "Bearer",
                result.token().expiresAt(),
                RegisterUserResponse.from(result.user()),
                result.recoveryCodes());
    }
}
