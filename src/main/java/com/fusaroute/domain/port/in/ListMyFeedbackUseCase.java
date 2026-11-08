package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Feedback;

import java.util.List;

/**
 * Ver mis comentarios con su estado y la respuesta (RF-08, SCRUM-22).
 */
public interface ListMyFeedbackUseCase {

    /**
     * Comentarios del usuario, del mas reciente al mas antiguo.
     *
     * Sin tope de filas, a diferencia del historial de busquedas (RF-07, que guarda
     * 6): un comentario es algo que el usuario escribio a mano y espera volver a
     * encontrar, no un rastro de su actividad que se pueda reciclar.
     *
     * Lista vacia si no ha escrito ninguno; no tener comentarios no es un error.
     */
    List<Feedback> myFeedback(Long userId);
}
