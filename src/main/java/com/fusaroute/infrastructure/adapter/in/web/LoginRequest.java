package com.fusaroute.infrastructure.adapter.in.web;

/**
 * Cuerpo de {@code POST /api/auth/login}. Sin anotaciones de validacion: el caso
 * de uso rechaza cualquier credencial invalida con el mismo 401 generico.
 *
 * {@code totpCode} es opcional: solo lo usa el usuario que ya tiene el segundo
 * factor activo (SCRUM-179). El mismo campo acepta un codigo TOTP de 6 digitos
 * o un codigo de respaldo.
 */
public record LoginRequest(String email, String password, String totpCode) {
}
