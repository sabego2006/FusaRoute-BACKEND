package com.fusaroute.infrastructure.adapter.in.web;

/**
 * Cuerpo de PUT /api/admin/comments/{id}/reply (RF-14, SCRUM-99 y SCRUM-100).
 *
 * @param reply respuesta del administrador. La validacion vive en el dominio
 *     ({@code Feedback.answeredWith}), no aqui. No se valida contra el banco de
 *     palabras ni contra el limite de 100 caracteres del comentario: son reglas para
 *     lo que escribe el publico, no para lo que responde quien administra.
 */
public record FeedbackReplyRequest(String reply) {
}
