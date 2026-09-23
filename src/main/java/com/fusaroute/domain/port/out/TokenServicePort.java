package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.User;

/**
 * Puerto de salida para el servicio de tokens.
 * Implementado por adaptadores de seguridad (ej. JWT).
 * Permite desacoplar el dominio de la implementación técnica del token.
 */
public interface TokenServicePort {
    /**
     * Genera un token de autenticación para el usuario dado.
     * @param user El usuario autenticado.
     * @return El token generado como String.
     */
    String generateToken(User user);
}
