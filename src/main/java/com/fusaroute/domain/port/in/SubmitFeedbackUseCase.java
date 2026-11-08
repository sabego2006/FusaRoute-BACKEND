package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Feedback;

/**
 * Dejar un comentario en la caja (RF-08, SCRUM-92).
 */
public interface SubmitFeedbackUseCase {

    /**
     * Guarda el comentario del usuario y lo devuelve con su id y su estado inicial.
     *
     * El nombre del autor NO se recibe por parametro: lo resuelve el caso de uso
     * leyendo el perfil del usuario autenticado. Si viniera del cuerpo de la
     * peticion, cualquiera podria firmar un comentario con el nombre de otro, y el
     * criterio de aceptacion pide justo lo contrario ("vinculado al perfil, no
     * anonimo").
     *
     * @throws com.fusaroute.domain.exception.FeedbackLengthException        si no mide entre 10 y 100
     * @throws com.fusaroute.domain.exception.FeedbackContainsUrlException   si trae un enlace
     * @throws com.fusaroute.domain.exception.FeedbackForbiddenWordException si usa una palabra del banco
     */
    Feedback submit(Long userId, String text);
}
