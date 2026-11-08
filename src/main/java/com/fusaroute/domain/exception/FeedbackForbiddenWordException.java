package com.fusaroute.domain.exception;

/**
 * El comentario usa una palabra del banco de palabras prohibidas (RF-08, SCRUM-91).
 *
 * El mensaje NO dice cual palabra se detecto, a proposito: repetirsela al usuario
 * convierte el error en un mapa del filtro y no agrega nada que el autor del
 * comentario no sepa ya.
 */
public class FeedbackForbiddenWordException extends RuntimeException {

    public FeedbackForbiddenWordException(String message) {
        super(message);
    }
}
