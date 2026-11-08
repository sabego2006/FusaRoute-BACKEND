package com.fusaroute.domain.port.in;

/**
 * Cuantos comentarios estan pendientes de respuesta.
 *
 * Existe para la METRICA 4 del panel de administrador de RF-13 (SCRUM-26), que es
 * trabajo de Santiago: el panel no consulta la tabla ni el repositorio, llama aqui.
 * Asi el "pendiente" del panel y el "pendiente" que bloquea la edicion de un
 * comentario son por construccion el mismo estado, que es lo que pide el criterio de
 * aceptacion ("mismo estado pendiente que usa RF-08/RF-14").
 */
public interface CountPendingFeedbackUseCase {

    long countPending();
}
