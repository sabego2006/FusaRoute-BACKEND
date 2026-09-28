package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.User;

/**
 * Comando para cambiar la contraseña del usuario.
 */
public record ChangePasswordCommand(
    Long userId,
    String currentPassword,
    String newPassword
) {}
