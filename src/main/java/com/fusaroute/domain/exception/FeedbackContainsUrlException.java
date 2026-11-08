package com.fusaroute.domain.exception;

/**
 * El comentario trae un enlace (RF-08, SCRUM-91).
 *
 * La regla existe porque la caja de comentarios es un canal directo al
 * administrador, no un tablon publico: sin ella se convierte en un sitio donde
 * sembrar enlaces de spam o de phishing a costa del proyecto.
 */
public class FeedbackContainsUrlException extends RuntimeException {

    public FeedbackContainsUrlException(String message) {
        super(message);
    }
}
