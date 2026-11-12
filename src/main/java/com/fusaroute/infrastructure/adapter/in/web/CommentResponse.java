package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Feedback;

import java.time.Instant;

/**
 * Un comentario tal como lo ve el cliente (RF-08 y RF-14, SCRUM-22 y SCRUM-23).
 *
 * Lleva {@code authorName} porque el criterio de aceptacion lo pide explicitamente:
 * el comentario NO es anonimo, muestra el nombre de quien lo escribio.
 *
 * No lleva {@code userId}, igual que {@link SearchHistoryResponse}: el id interno de
 * la cuenta no le sirve a ninguna pantalla y exponerlo permitiria contar y relacionar
 * usuarios desde el cliente. El nombre ya identifica al autor para lo que la interfaz
 * necesita.
 *
 * {@code mine} dice si quien mira es el autor. Existe porque el feed compartido
 * mezcla comentarios de todos y el cliente no conoce ningun id de usuario con el cual
 * compararse: el backend lo resuelve contra el sujeto del JWT y manda solo el booleano.
 *
 * {@code editable} se calcula aqui y se manda resuelto en vez de dejar que el
 * frontend lo deduzca del estado: es el comentario PROPIO y aun pendiente. Es una
 * sola fuente de verdad: si manana la regla de "hasta cuando se puede editar"
 * cambia, cambia en el dominio y el boton de Angular se adapta sin tocar el
 * frontend. Ojo con lo que NO es: esconder el boton no es seguridad, y el backend
 * vuelve a verificar la regla en cada PUT y DELETE.
 *
 * @param reply      respuesta del administrador, o {@code null} si sigue pendiente.
 *                   No lleva el nombre de quien respondio.
 * @param answeredAt cuando se respondio, o {@code null} si sigue pendiente.
 * @param mine       {@code true} solo si quien consulta es el autor.
 */
public record CommentResponse(Long id, String authorName, String text, String status,
                              String reply, Instant answeredAt, Instant createdAt,
                              Instant updatedAt, boolean editable, boolean mine) {

    public static CommentResponse from(Feedback feedback, Long viewerId) {
        boolean mine = feedback.getUserId().equals(viewerId);
        return new CommentResponse(
                feedback.getId(),
                feedback.getAuthorName(),
                feedback.getText(),
                feedback.getStatus().name(),
                feedback.getReply(),
                feedback.getAnsweredAt(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt(),
                mine && feedback.isPending(),
                mine);
    }
}
