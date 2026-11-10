package com.fusaroute.domain.exception;

/**
 * Una punta de la busqueda (origen o destino) no se puede resolver a una coordenada: el lugar no existe,
 * el nombre no corresponde a ningun lugar conocido o falta el dato. Es error del cliente (400) y dice
 * CUAL de las dos puntas fallo.
 */
public class InvalidSearchPointException extends RuntimeException {

    private final String side;

    /**
     * @param side {@code "origin"} o {@code "destination"}, tal como se llaman los parametros
     * @param reason por que no se pudo resolver
     */
    public InvalidSearchPointException(String side, String reason) {
        super("No se pudo resolver el " + ("origin".equals(side) ? "origen" : "destino") + ": " + reason);
        this.side = side;
    }

    public String side() {
        return side;
    }
}
