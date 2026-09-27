package com.fusaroute.domain.port.out;

/**
 * Puerto de salida para hashear contrasenas. El dominio decide cuando hashear;
 * el algoritmo concreto (BCrypt) vive en un adaptador de infraestructura.
 * SCRUM-13 (login) le agregara {@code matches} para verificar.
 */
public interface PasswordHasherPort {

    String hash(String raw);
}
