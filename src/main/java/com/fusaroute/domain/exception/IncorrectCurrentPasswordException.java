package com.fusaroute.domain.exception;

/**
 * Excepción de dominio lanzada cuando la contraseña actual proporcionada no coincide
 * con la almacenada, impidiendo el cambio de contraseña.
 */
public class IncorrectCurrentPasswordException extends RuntimeException {
    public IncorrectCurrentPasswordException() {
        super("La contraseña actual es incorrecta.");
    }
}
