package com.fusaroute.domain.port.out;

/**
 * Puerto de salida para hashear y verificar contrasenas. El dominio decide cuando;
 * el algoritmo concreto (BCrypt) vive en un adaptador de infraestructura.
 */
public interface PasswordHasherPort {

    String hash(String raw);

    /**
     * Verifica una contrasena contra un hash guardado.
     *
     * Contrato: si {@code hashOrNull} es null (el correo no existe) la
     * implementacion debe hacer el mismo trabajo costoso que con un hash real y
     * devolver false. Asi el tiempo de respuesta no delata si el correo tiene cuenta.
     */
    boolean matches(String raw, String hashOrNull);
}
