package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.User;

/**
 * Respuesta del registro (201). NUNCA incluye el hash de la contrasena ni ningun
 * dato sensible.
 */
public record RegisterUserResponse(Long id, String name, String email, String role, boolean active) {

    static RegisterUserResponse from(User user) {
        return new RegisterUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail().value(),
                user.getRole().name(),
                user.isActive());
    }
}
