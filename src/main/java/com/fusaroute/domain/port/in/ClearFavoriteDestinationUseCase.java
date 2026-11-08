package com.fusaroute.domain.port.in;

/**
 * Quitar el destino favorito (RF-10, SCRUM-21).
 */
public interface ClearFavoriteDestinationUseCase {

    /**
     * Deja al usuario sin favorito. Es idempotente: quitarlo cuando no habia ninguno
     * no es un error, porque el resultado que el usuario pidio ya se cumple.
     */
    void clear(Long userId);
}
