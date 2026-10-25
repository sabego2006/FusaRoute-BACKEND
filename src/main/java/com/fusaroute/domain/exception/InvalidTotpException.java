package com.fusaroute.domain.exception;

/**
 * El codigo del segundo factor es incorrecto, o el codigo de respaldo no sirve
 * (ya se gasto o nunca existio). SCRUM-179.
 *
 * Lanzada tanto en login (tras validar contrasena) como en confirmacion de
 * enrolamiento. Termina en un 401 con un cuerpo que el frontend reconoce
 * ({@code error:"INVALID_TOTP"}).
 */
public class InvalidTotpException extends RuntimeException {

    public InvalidTotpException() {
        super("Codigo del segundo factor incorrecto");
    }
}
