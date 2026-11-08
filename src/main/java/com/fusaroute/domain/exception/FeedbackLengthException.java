package com.fusaroute.domain.exception;

/**
 * El comentario no mide entre 10 y 100 caracteres (RF-08, SCRUM-91).
 *
 * Cada regla de validacion del comentario tiene su propia excepcion en vez de una
 * sola generica con un codigo adentro. La razon es practica: el frontend muestra el
 * mensaje tal como llega, y el {@code GlobalExceptionHandler} necesita poder darle
 * un trato distinto a cada una si manana una deja de ser 400.
 */
public class FeedbackLengthException extends RuntimeException {

    public FeedbackLengthException(String message) {
        super(message);
    }
}
