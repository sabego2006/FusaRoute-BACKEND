package com.fusaroute.domain.exception;

/**
 * Se intento editar o borrar un comentario que el administrador ya respondio
 * (RF-08, SCRUM-94 y SCRUM-95).
 *
 * Sale como 409 y no como 403: el permiso esta bien —es su comentario— y lo que
 * choca es el estado del recurso. Esa distincion importa para el frontend, que
 * ante un 409 debe recargar el comentario para mostrar la respuesta que acaba de
 * aparecer, no mandar al usuario a iniciar sesion de nuevo.
 *
 * La regla protege la metrica 4 del panel de RF-13: si el autor pudiera reescribir
 * un comentario ya respondido, el conteo de pendientes dejaria de corresponder con
 * lo que el administrador reviso.
 */
public class FeedbackAlreadyAnsweredException extends RuntimeException {

    public FeedbackAlreadyAnsweredException(String message) {
        super(message);
    }
}
