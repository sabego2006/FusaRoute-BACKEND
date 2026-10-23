package com.fusaroute.domain.port.in;

/**
 * Datos para confirmar el enrolamiento TOTP (SCRUM-179). El codigo es el primero
 * que la app autenticadora genera tras escanear el QR; sirve para probar que
 * usuario y servidor compartieron el secreto correctamente.
 */
public record ConfirmTotpCommand(Long userId, String code) {
}
