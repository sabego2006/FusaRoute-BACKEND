package com.fusaroute.domain.exception;

/**
 * La operacion choca con el estado actual: nombre o letrero repetido, o restaurar una
 * ruta que no estaba inactiva. Termina en un 409.
 */
public class RouteConflictException extends RuntimeException {

    public RouteConflictException(String message) {
        super(message);
    }
}
