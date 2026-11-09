package com.fusaroute.domain.exception;

import com.fusaroute.domain.model.NearestRoute;

/**
 * Excepción de negocio lanzada cuando no se encuentra ninguna ruta que conecte
 * el origen y el destino solicitados.
 *
 * Desde P1 puede llevar la ruta con trazado mas cercana a cada punta ({@link #nearestToOrigin()},
 * {@link #nearestToDestination()}), para que el cliente le diga al usuario cuanto le falta.
 */
public class NoRouteAvailableException extends RuntimeException {

    private final transient NearestRoute nearestToOrigin;
    private final transient NearestRoute nearestToDestination;

    public NoRouteAvailableException(String origin, String destination) {
        this(origin, destination, null, null);
    }

    public NoRouteAvailableException(String origin, String destination,
                                     NearestRoute nearestToOrigin, NearestRoute nearestToDestination) {
        super(String.format("No hay rutas disponibles que conecten %s con %s", origin, destination));
        this.nearestToOrigin = nearestToOrigin;
        this.nearestToDestination = nearestToDestination;
    }

    /** Ruta mas cercana al origen; {@code null} si no se pudo calcular. */
    public NearestRoute nearestToOrigin() {
        return nearestToOrigin;
    }

    /** Ruta mas cercana al destino; {@code null} si no se pudo calcular. */
    public NearestRoute nearestToDestination() {
        return nearestToDestination;
    }
}
