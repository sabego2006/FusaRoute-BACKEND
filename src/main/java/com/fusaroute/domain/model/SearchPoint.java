package com.fusaroute.domain.model;

import java.util.Objects;

/**
 * Una punta de la busqueda tal como la manda el cliente. Son tres formas, y el controller escoge
 * UNA por punta con esta prioridad: lugar canonico, coordenada, nombre (legado).
 */
public sealed interface SearchPoint {

    /** Lugar canonico elegido por id en el selector ({@code GET /api/places}). */
    record OfPlace(long placeId) implements SearchPoint {
    }

    /** GPS del dispositivo o punto tocado en el mapa. */
    record OfCoordinate(Coordinate coordinate) implements SearchPoint {
        public OfCoordinate {
            Objects.requireNonNull(coordinate, "coordinate");
        }
    }

    /**
     * Nombre de barrio o lugar (parametro legado {@code originNeighborhood}). Se resuelve por nombre o
     * alias normalizado al lugar canonico.
     */
    record OfName(String name) implements SearchPoint {
        public OfName {
            Objects.requireNonNull(name, "name");
        }
    }
}
