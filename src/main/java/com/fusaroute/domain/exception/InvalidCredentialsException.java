package com.fusaroute.domain.exception;

/**
 * Las credenciales no sirven para iniciar sesion. Es UNA sola excepcion con UN
 * solo mensaje para todos los motivos (correo inexistente, contrasena incorrecta,
 * cuenta inactiva, formato invalido): distinguirlos le diria a un atacante que
 * correos tienen cuenta. Termina en un 401.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Correo o contrasena incorrectos");
    }
}
