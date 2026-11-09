package com.fusaroute.domain.model;

import java.util.Optional;

/**
 * Punta de la busqueda ya resuelta.
 *
 * @param coordinate donde esta; {@code null} solo para un nombre legado que no corresponde a ningun
 *     lugar con coordenada (solo puede servir contra rutas sin trazado, por nombre)
 * @param label como se muestra: el nombre del lugar canonico, o "Cerca de &lt;lugar&gt;" si llego por
 *     coordenadas
 * @param place el lugar canonico, si la punta es (o se resolvio a) uno
 */
public record ResolvedPoint(Coordinate coordinate, String label, Place place) {

    public Optional<Place> findPlace() {
        return Optional.ofNullable(place);
    }

    public boolean hasCoordinate() {
        return coordinate != null;
    }
}
