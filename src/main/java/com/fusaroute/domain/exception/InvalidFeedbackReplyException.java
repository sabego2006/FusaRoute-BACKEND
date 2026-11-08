package com.fusaroute.domain.exception;

/**
 * La respuesta del administrador llego vacia o demasiado larga (RF-14, SCRUM-99).
 *
 * Es excepcion de dominio aparte de las del texto del comentario porque la regla es
 * otra: la respuesta no se valida contra el banco de palabras ni contra el limite de
 * 100 caracteres del comentario, y puede necesitar mas espacio para explicar algo.
 */
public class InvalidFeedbackReplyException extends RuntimeException {

    public InvalidFeedbackReplyException(String message) {
        super(message);
    }
}
