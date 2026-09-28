package com.fusaroute.domain.port.in;

/**
 * Datos que el usuario envia al actualizar su perfil (RF-03). La contrasena
 * se cambia por separado con {@link ChangePasswordCommand}.
 */
public record UpdateProfileCommand(Long userId, String name, String email, String phone) {
}
