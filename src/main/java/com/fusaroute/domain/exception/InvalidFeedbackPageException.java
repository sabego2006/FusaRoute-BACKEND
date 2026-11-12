package com.fusaroute.domain.exception;

/**
 * Se pidio una pagina del feed de comentarios que no existe: pagina negativa o
 * tamano menor que 1 (feed de comentarios compartido).
 *
 * Un tamano por encima del tope no es este error: se recorta al maximo en vez de rechazarse,
 * porque pedir mas de lo permitido no es una peticion mal formada, solo exagerada.
 */
public class InvalidFeedbackPageException extends RuntimeException {

    public InvalidFeedbackPageException(String message) {
        super(message);
    }
}
