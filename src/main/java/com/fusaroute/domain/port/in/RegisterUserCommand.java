package com.fusaroute.domain.port.in;

/**
 * Datos crudos del registro, tal como llegan del mundo exterior y antes de
 * cualquier validacion o normalizacion. El telefono no entra en el registro
 * (RF-03 lo deja para el perfil).
 */
public record RegisterUserCommand(String name, String email, String password) {
}
