package com.fusaroute.domain.exception;

/**
 * La ruta no existe o no esta publicada. Una ruta suspendida se trata igual que una
 * inexistente a proposito: el catalogo publico no revela que existe. Termina en un 404.
 */
public class RouteNotFoundException extends RuntimeException {

    public RouteNotFoundException() {
        super("Ruta no encontrada");
    }
}
