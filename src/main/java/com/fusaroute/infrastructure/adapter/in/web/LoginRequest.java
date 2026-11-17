package com.fusaroute.infrastructure.adapter.in.web;

/**
 * Cuerpo de {@code POST /api/auth/login}. Sin anotaciones de validacion: el caso
 * de uso rechaza cualquier credencial invalida con el mismo 401 generico.
 *
 * {@code totpCode} es opcional: solo lo usa el usuario que ya tiene el segundo
 * factor activo (SCRUM-179). El mismo campo acepta un codigo TOTP de 6 digitos
 * o un codigo de respaldo.
 *
 * {@code trustToken} es opcional y nuevo (decision del 17-nov-2026, SCRUM-196): el
 * dispositivo de confianza guardado de un login anterior, para que un USER no
 * repita el TOTP durante 15 dias en el mismo dispositivo. Un cliente viejo que
 * no lo envia sigue funcionando igual que antes.
 */
public record LoginRequest(String email, String password, String totpCode, String trustToken) {
}
