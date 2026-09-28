package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.User;

/**
 * Comando para actualizar los datos del perfil.
 */
public record UpdateProfileCommand(
    Long userId,
    String name,
    String email,
    String phone
) {}
