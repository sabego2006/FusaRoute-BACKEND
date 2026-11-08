package com.fusaroute.domain.port.in;

/**
 * Borrar un comentario propio mientras siga pendiente (RF-08, SCRUM-95).
 */
public interface DeleteFeedbackUseCase {

    /**
     * Borra el comentario {@code feedbackId}.
     *
     * NO es idempotente, a diferencia de quitar el destino favorito (RF-10): borrar
     * un comentario que no existe responde 404. La diferencia es que alla el recurso
     * es un estado de la cuenta ("no tengo favorito") y aqui es un objeto con id, y
     * un 204 ante un id inventado le diria al frontend que borro algo que nunca
     * existio.
     *
     * @throws com.fusaroute.domain.exception.FeedbackNotFoundException        si no existe o es de otro usuario
     * @throws com.fusaroute.domain.exception.FeedbackAlreadyAnsweredException si ya fue respondido
     */
    void delete(Long userId, Long feedbackId);
}
