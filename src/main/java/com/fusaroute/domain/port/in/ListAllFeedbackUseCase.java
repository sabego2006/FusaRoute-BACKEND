package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;

import java.util.List;

/**
 * Listado de comentarios para el administrador (RF-14, SCRUM-98).
 *
 * Son dos metodos y no uno con un parametro que acepte nulo: "todos, pendientes
 * primero" y "solo los de un estado" son dos consultas distintas con orden distinto,
 * y un {@code null} en la firma obliga a leer la implementacion para saber que hace.
 */
public interface ListAllFeedbackUseCase {

    /**
     * Todos los comentarios, con los PENDIENTES PRIMERO y dentro de cada grupo del
     * mas reciente al mas antiguo.
     *
     * El orden no es cosmetico: es el criterio de aceptacion de RF-14 ("filtrable por
     * pendientes primero"), y responde a lo que el administrador va a hacer con la
     * pantalla, que es despachar lo que falta por responder.
     */
    List<Feedback> allFeedbackPendingFirst();

    /** Solo los comentarios del estado pedido, del mas reciente al mas antiguo. */
    List<Feedback> allFeedbackWithStatus(FeedbackStatus status);
}
