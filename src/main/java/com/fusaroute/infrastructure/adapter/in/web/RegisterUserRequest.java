package com.fusaroute.infrastructure.adapter.in.web;

/**
 * Cuerpo de {@code POST /api/auth/register}. El telefono no entra en el registro
 * (RF-03 lo deja para el perfil). No se valida aqui con anotaciones: la validacion
 * de negocio vive en el dominio, que devuelve todos los errores juntos.
 */
public record RegisterUserRequest(String name, String email, String password) {
}
