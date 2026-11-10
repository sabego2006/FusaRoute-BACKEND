package com.fusaroute.domain.exception;

/** Datos del viaje "Ya me subi" incompletos o fuera de rango: termina en 400. */
public class InvalidRideException extends RuntimeException {

    public InvalidRideException(String message) {
        super(message);
    }
}
