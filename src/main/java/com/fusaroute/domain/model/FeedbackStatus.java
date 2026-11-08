package com.fusaroute.domain.model;

/**
 * Estado de un comentario (RF-08 / RF-14, SCRUM-22 y SCRUM-23).
 *
 * Son dos y no tres a proposito: el criterio de aceptacion de RF-14 dice "una
 * respuesta por comentario, no hilo de conversacion", asi que no hay un estado
 * intermedio de "en conversacion". El paso de {@link #PENDING} a {@link #ANSWERED}
 * es de ida: responder de nuevo edita la respuesta existente y no devuelve el
 * comentario a pendiente.
 *
 * {@link #PENDING} es tambien el estado que cuenta la metrica 4 del panel de RF-13
 * ("comentarios pendientes de respuesta"), de ahi que el bloqueo de edicion y
 * borrado dependa de este enum y no de un booleano aparte: una sola fuente de
 * verdad para las dos historias.
 */
public enum FeedbackStatus {

    /** Nadie lo ha respondido. El autor todavia puede editarlo o borrarlo. */
    PENDING,

    /** El administrador ya respondio. Queda bloqueado para el autor. */
    ANSWERED
}
