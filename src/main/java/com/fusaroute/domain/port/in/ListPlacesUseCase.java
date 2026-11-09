package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Place;

import java.util.List;

/** Catalogo de lugares que el usuario puede elegir como origen o destino (P1, {@code GET /api/places}). */
public interface ListPlacesUseCase {

    /** @param routeCount rutas activas con trazado que pasan a distancia de caminata del lugar */
    record PlaceSummary(Place place, int routeCount) {
    }

    /** Todos los lugares con coordenada, ordenados por nombre. */
    List<PlaceSummary> listPlaces();
}
