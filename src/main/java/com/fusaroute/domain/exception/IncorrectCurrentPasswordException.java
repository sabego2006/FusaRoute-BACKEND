package com.fusaroute.domain.exception;

/** La contrasena actual proporcionada no coincide con la almacenada. */
public class IncorrectCurrentPasswordException extends RuntimeException {

    public IncorrectCurrentPasswordException() {
        super("La contrasena actual no es correcta");
    }
}
