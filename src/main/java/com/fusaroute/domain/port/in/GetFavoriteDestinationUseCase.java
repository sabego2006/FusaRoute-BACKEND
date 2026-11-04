package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.FavoriteDestination;

import java.util.Optional;

/**
 * Consultar el destino favorito del usuario (RF-10, SCRUM-21).
 */
public interface GetFavoriteDestinationUseCase {

    /** Vacio si el usuario no ha marcado ninguno; no tener favorito no es un error. */
    Optional<FavoriteDestination> of(Long userId);
}
