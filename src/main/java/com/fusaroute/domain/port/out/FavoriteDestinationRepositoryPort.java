package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.FavoriteDestination;

import java.util.Optional;

/**
 * Persistencia del destino favorito (RF-10, SCRUM-21).
 */
public interface FavoriteDestinationRepositoryPort {

    /**
     * Guarda el favorito del usuario, reemplazando el que tuviera.
     *
     * No hay un {@code update} aparte a proposito: con un solo favorito por cuenta,
     * "marcar" y "cambiar" son la misma operacion para el usuario, y partirlas en dos
     * metodos obligaria a quien llama a preguntar antes si ya habia uno.
     */
    FavoriteDestination save(FavoriteDestination favorite);

    /** Vacio si el usuario no ha marcado ninguno. */
    Optional<FavoriteDestination> findByUser(Long userId);

    /** Sin efecto si no habia favorito. */
    void deleteByUser(Long userId);
}
