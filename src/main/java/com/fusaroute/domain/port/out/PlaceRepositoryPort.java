package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.Place;

import java.util.List;
import java.util.Optional;

/** Acceso a los lugares canonicos con coordenada (V21). Solo lectura. */
public interface PlaceRepositoryPort {

    /** Todos los lugares, con sus alias. Son un par de cientos de filas que cambian solo por migracion. */
    List<Place> findAll();

    Optional<Place> findById(Long id);
}
