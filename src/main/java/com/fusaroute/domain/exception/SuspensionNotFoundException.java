package com.fusaroute.domain.exception;

/** La suspension no existe (RF-12). Termina en un 404. */
public class SuspensionNotFoundException extends RuntimeException {

    public SuspensionNotFoundException() {
        super("Suspension no encontrada");
    }
}
