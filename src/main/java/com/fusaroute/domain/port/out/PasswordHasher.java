package com.fusaroute.domain.port.out;

/**
 * Puerto de salida para el hasheo de contraseñas.
 * Implementado por adaptadores de seguridad (ej. BCrypt).
 * Mantiene el dominio libre de dependencias de Spring Security.
 */
public interface PasswordHasher {
    String hash(String rawPassword);
    boolean verify(String rawPassword, String hashedPassword);
}
