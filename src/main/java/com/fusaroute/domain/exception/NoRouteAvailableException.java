package com.fusaroute.domain.exception;

/**
 * Excepción de negocio lanzada cuando no se encuentra ninguna ruta que conecte
 * el origen y el destino solicitados.
 */
public class NoRouteAvailableException extends RuntimeException {
    public NoRouteAvailableException(String origin, String destination) {
        super(String.format("No hay rutas disponibles que conecten %s con %s", origin, destination));
    }
}
