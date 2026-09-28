package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.LoginResult;

import java.time.Instant;

/**
 * Respuesta del login (200). El {@code token} va en el header
 * {@code Authorization: Bearer <token>} de las peticiones autenticadas.
 * NUNCA incluye el hash de la contrasena: el usuario sale por
 * {@link RegisterUserResponse}, que no lo tiene.
 */
public record LoginResponse(String token, String tokenType, Instant expiresAt, RegisterUserResponse user) {

    static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.token().value(),
                "Bearer",
                result.token().expiresAt(),
                RegisterUserResponse.from(result.user()));
    }
}
