package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Feedback;

/**
 * Editar un comentario propio mientras siga pendiente (RF-08, SCRUM-94).
 */
public interface EditFeedbackUseCase {

    /**
     * Reemplaza el texto del comentario {@code feedbackId}.
     *
     * @throws com.fusaroute.domain.exception.FeedbackNotFoundException        si no existe o es de otro usuario
     * @throws com.fusaroute.domain.exception.FeedbackAlreadyAnsweredException si ya fue respondido
     */
    Feedback edit(Long userId, Long feedbackId, String newText);
}
