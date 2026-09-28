package com.fusaroute.infrastructure.adapter.in.web;

/**
 * Cuerpo de {@code POST /api/auth/login}. Sin anotaciones de validacion: el caso
 * de uso rechaza cualquier credencial invalida con el mismo 401 generico.
 */
public record LoginRequest(String email, String password) {
}
