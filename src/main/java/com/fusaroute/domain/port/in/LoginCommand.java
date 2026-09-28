package com.fusaroute.domain.port.in;

/**
 * Credenciales crudas del inicio de sesion, tal como llegan del mundo exterior y
 * antes de cualquier validacion o normalizacion.
 */
public record LoginCommand(String email, String password) {
}
