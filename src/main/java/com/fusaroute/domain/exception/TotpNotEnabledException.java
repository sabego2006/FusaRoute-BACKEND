package com.fusaroute.domain.exception;

/**
 * El usuario pidio algo que solo tiene sentido con el segundo factor ya activo
 * — hoy, regenerar sus codigos de respaldo (SCRUM-179).
 *
 * Es el caso espejo de {@link TotpAlreadyEnabledException}, y se mapea al mismo
 * 409: el estado del recurso no permite la operacion. No se usa un 401 porque
 * el usuario SI esta autenticado; lo que falta es el factor.
 */
public class TotpNotEnabledException extends RuntimeException {

    public TotpNotEnabledException() {
        super("El segundo factor no esta activo");
    }
}
