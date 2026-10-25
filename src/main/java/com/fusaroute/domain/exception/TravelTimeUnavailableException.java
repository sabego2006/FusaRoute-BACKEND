package com.fusaroute.domain.exception;

/**
 * El proveedor de tiempos de viaje (Google Maps) no respondio a tiempo o devolvio
 * un error. El dominio no conoce HTTP ni el proveedor concreto: el adaptador
 * traduce cualquier fallo de red o de la API externa a esta excepcion.
 */
public class TravelTimeUnavailableException extends RuntimeException {

    public TravelTimeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
