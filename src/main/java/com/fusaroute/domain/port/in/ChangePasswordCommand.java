package com.fusaroute.domain.port.in;

/**
 * Datos para cambiar la contrasena del usuario autenticado (RF-03). Exige la
 * contrasena actual como verificacion.
 */
public record ChangePasswordCommand(Long userId, String currentPassword, String newPassword) {
}
