package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.FavoriteDestination;

/**
 * Marcar un destino como favorito (RF-10, SCRUM-21).
 */
public interface SetFavoriteDestinationUseCase {

    /**
     * Deja {@code destination} como el favorito del usuario. Si ya tenia otro, lo
     * reemplaza: solo hay un favorito activo a la vez.
     *
     * @return el favorito tal como quedo guardado
     */
    FavoriteDestination set(Long userId, String destination);
}
