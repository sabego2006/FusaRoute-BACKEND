package com.fusaroute.infrastructure.adapter.in.web;

/**
 * Cuerpo de GET /api/comments/me/unread-count (RF-14, SCRUM-101).
 *
 * Va como objeto y no como un numero suelto para que el cuerpo sea JSON valido y
 * para poder agregarle campos despues (por ejemplo, el instante del ingreso anterior)
 * sin romper a los clientes existentes.
 *
 * @param count respuestas recibidas desde el ingreso ANTERIOR del usuario. Cero
 *     cuando es su primer ingreso: no hay un "antes" contra el que comparar.
 */
public record UnreadRepliesResponse(long count) {
}
