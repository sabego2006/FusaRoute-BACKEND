package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Feedback;

/**
 * Responder un comentario (RF-14, SCRUM-99 y SCRUM-100).
 */
public interface ReplyToFeedbackUseCase {

    /**
     * Deja {@code reply} como LA respuesta del comentario y lo pasa a respondido.
     *
     * Sirve para la primera respuesta y para corregirla: el criterio de aceptacion
     * dice "una respuesta por comentario; si el administrador necesita corregir su
     * respuesta, la edita, no crea una segunda". Por eso no hay un {@code update}
     * aparte — responder dos veces reescribe la misma columna.
     *
     * NO recibe el id del administrador. En el alcance actual la respuesta no se
     * atribuye a una persona: hay un solo rol de administrador y el usuario ve "la
     * respuesta", no "la respuesta de tal". Si manana hiciera falta saber quien
     * respondio, es una columna nueva y su migracion, no un cambio de esta firma.
     *
     * @throws com.fusaroute.domain.exception.FeedbackNotFoundException    si el comentario no existe
     * @throws com.fusaroute.domain.exception.InvalidFeedbackReplyException si la respuesta va vacia o es muy larga
     */
    Feedback reply(Long feedbackId, String reply);
}
