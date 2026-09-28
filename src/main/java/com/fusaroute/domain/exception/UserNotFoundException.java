package com.fusaroute.domain.exception;

/**
 * Excepción de dominio lanzada cuando un usuario solicitado por su ID no existe.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("Usuario con ID " + id + " no encontrado.");
    }
}
