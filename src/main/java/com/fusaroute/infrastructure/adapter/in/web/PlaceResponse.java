package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.ListPlacesUseCase.PlaceSummary;

import java.util.List;

/**
 * Lugar del selector de origen y destino ({@code GET /api/places}).
 *
 * @param id lo que el cliente manda como {@code originPlaceId} / {@code destinationPlaceId}
 * @param aliases otras formas en que la gente llama al lugar; el selector puede buscarlas tambien
 * @param routeCount rutas activas con trazado que pasan a menos de 800 m en linea recta
 */
public record PlaceResponse(Long id, String slug, String name, double lat, double lng,
                            List<String> aliases, int routeCount) {

    static PlaceResponse from(PlaceSummary summary) {
        return new PlaceResponse(summary.place().id(), summary.place().slug(), summary.place().name(),
                summary.place().coordinate().latitude(), summary.place().coordinate().longitude(),
                summary.place().aliases(), summary.routeCount());
    }
}
