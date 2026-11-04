package com.fusaroute.domain.exception;

/**
 * El destino que se quiso marcar como favorito no sirve (RF-10, SCRUM-21).
 *
 * Es excepcion de dominio y no {@code IllegalArgumentException} porque el valor llega
 * del cuerpo de una peticion del usuario: tiene que salir como 400 con un mensaje que
 * el frontend pueda mostrar, y {@code GlobalExceptionHandler} no traduce las
 * excepciones genericas de Java — sin esto el usuario veria un 500.
 */
public class InvalidFavoriteDestinationException extends RuntimeException {

    public InvalidFavoriteDestinationException(String message) {
        super(message);
    }
}
