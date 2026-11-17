package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.LoginResult;

import java.time.Instant;

/**
 * Respuesta del login (200). El {@code token} va en el header
 * {@code Authorization: Bearer <token>} de las peticiones autenticadas.
 * NUNCA incluye el hash de la contrasena: el usuario sale por
 * {@link RegisterUserResponse}, que no lo tiene.
 *
 * {@code trustToken} es el dispositivo de confianza (decision del
 * 17-nov-2026, SCRUM-196): viene no nulo solo cuando el login acaba de verificar un
 * codigo TOTP correcto de una cuenta USER. El frontend lo guarda y lo
 * reenvia en el siguiente login; si viene nulo, conserva el que ya tenia.
 */
public record LoginResponse(String token, String tokenType, Instant expiresAt, RegisterUserResponse user,
                             String trustToken) {

    static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.token().value(),
                "Bearer",
                result.token().expiresAt(),
                RegisterUserResponse.from(result.user()),
                result.trustToken());
    }
}
