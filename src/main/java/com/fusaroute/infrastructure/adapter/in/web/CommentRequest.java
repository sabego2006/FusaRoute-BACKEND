package com.fusaroute.infrastructure.adapter.in.web;

/**
 * Cuerpo de POST /api/comments y de PUT /api/comments/{id} (RF-08, SCRUM-92 y SCRUM-94).
 *
 * El MISMO record sirve para crear y para editar porque el cuerpo es identico: un
 * texto. Partirlo en dos records con el mismo campo unico solo duplicaria el javadoc.
 *
 * No lleva el nombre del autor, a proposito: lo resuelve el backend leyendo el perfil
 * del usuario autenticado. Si viniera aqui, cualquiera podria firmar un comentario
 * con el nombre de otro.
 *
 * @param text texto del comentario. La validacion (10 a 100 caracteres, sin enlaces,
 *     sin palabras del banco) vive en el dominio ({@code Feedback}), no aqui.
 */
public record CommentRequest(String text) {
}
