package com.fusaroute.domain.model;

import java.time.Duration;
import java.util.Optional;

/**
 * Resultado de una búsqueda de ruta (RF-04): la ruta elegida como mejor opción, el
 * tiempo estimado para recorrerla, y la ultima milla en cada punta — por donde se
 * aborda y por donde se baja.
 *
 * @param route ruta seleccionada
 * @param estimatedTime tiempo estimado del trayecto en buseta
 * @param boardingPoint donde tomar la buseta; {@code null} si la ruta no tiene
 *     trazado cargado todavia
 * @param dropOffPoint donde bajarse; {@code null} por la misma razon
 */
public record RouteSearchResult(Route route, Duration estimatedTime,
                                BoardingPoint boardingPoint, BoardingPoint dropOffPoint) {

    /** Donde tomar la buseta, vacio si la ruta todavia no tiene trazado. */
    public Optional<BoardingPoint> boarding() {
        return Optional.ofNullable(boardingPoint);
    }

    /** Donde bajarse, vacio si la ruta todavia no tiene trazado. */
    public Optional<BoardingPoint> dropOff() {
        return Optional.ofNullable(dropOffPoint);
    }
}
