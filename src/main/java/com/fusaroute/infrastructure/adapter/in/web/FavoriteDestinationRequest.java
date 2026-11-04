package com.fusaroute.infrastructure.adapter.in.web;

/**
 * Cuerpo de PUT /api/users/me/favorite-destination (RF-10, SCRUM-21).
 *
 * Va como objeto y no como un String suelto o un parametro de consulta para que
 * anadir campos despues (por ejemplo, un alias del favorito) no rompa a los clientes
 * que ya existan.
 *
 * @param destination barrio que se quiere dejar como favorito. La validacion vive en
 *     el dominio ({@code FavoriteDestination}), no aqui.
 */
public record FavoriteDestinationRequest(String destination) {
}
