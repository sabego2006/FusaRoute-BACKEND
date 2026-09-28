package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.User;

/**
 * Respuesta del perfil del usuario (GET y PUT /api/users/me). NUNCA incluye
 * el hash de la contrasena ni datos sensibles.
 */
public record UserProfileResponse(Long id, String name, String email, String phone, String role) {

    static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail().value(),
                user.getPhone(),
                user.getRole().name());
    }
}
