package com.fusaroute.domain.exception;

/**
 * Una coordenada fuera del rango geografico valido (latitud en [-90, 90], longitud en
 * [-180, 180]). Es un error de quien llama, no un fallo del servidor: la capa web lo
 * traduce a 400.
 */
public class InvalidCoordinateException extends IllegalArgumentException {

    public InvalidCoordinateException(String message) {
        super(message);
    }
}
