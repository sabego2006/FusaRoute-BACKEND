package com.fusaroute.application.dto;

/**
 * DTO para la petición de inicio de sesión.
 */
public record LoginRequest(String email, String password) {}
