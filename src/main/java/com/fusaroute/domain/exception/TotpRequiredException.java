package com.fusaroute.domain.exception;

/**
 * El usuario envio contrasena correcta pero tiene TOTP activo y falto el codigo
 * (SCRUM-179). Es un rechazo distinguible del 401 generico de credenciales: ya
 * se valido la contrasena, lo que falta es el segundo factor, y el frontend lo
 * usa para mostrar el paso del codigo.
 */
public class TotpRequiredException extends RuntimeException {

    public TotpRequiredException() {
        super("Se requiere el codigo del segundo factor");
    }
}
