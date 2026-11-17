package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;

/**
 * Resultado de un inicio de sesion exitoso: el usuario autenticado y su token.
 *
 * {@code trustToken} es el dispositivo de confianza del segundo factor
 * (decision del 17-nov-2026, SCRUM-196): no nulo solo cuando un USER acaba de verificar
 * un codigo TOTP correcto en esta misma peticion. En cualquier otro caso
 * (salto por un token ya existente, codigo de respaldo, cuenta ADMIN) viene
 * nulo, y el cliente simplemente conserva el que ya tenia guardado.
 */
public record LoginResult(User user, IssuedToken token, String trustToken) {
}
