package com.fusaroute.infrastructure.adapter.in.web;

/**
 * Cuerpo de {@code POST /api/users/me/totp/confirm} (SCRUM-179). El codigo es
 * el primero que la app autenticadora genera tras escanear el QR.
 */
public record ConfirmTotpRequest(String code) {
}
