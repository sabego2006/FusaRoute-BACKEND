package com.fusaroute.domain.exception;

/** El usuario no existe en el sistema. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("El usuario no fue encontrado");
    }
}
