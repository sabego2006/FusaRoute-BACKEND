package com.fusaroute.domain.model;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Resultado de una búsqueda de ruta (RF-04): la ruta elegida como mejor opción y la
 * ultima milla en cada punta — por donde se aborda y por donde se baja.
 *
 * Lo que la hace "mejor" depende del modo. Online gana la de menor tiempo, que lo
 * informa Google; offline no hay red para preguntar, asi que gana la de menor
 * recorrido sobre el trazado propio. Por eso {@code estimatedTime} esta vacio en
 * offline: inventar un tiempo sin consultar seria peor que no darlo.
 *
 * @param route ruta seleccionada
 * @param estimatedTime tiempo del trayecto en buseta; {@code null} en modo offline
 * @param travelDistanceInMeters recorrido en buseta entre abordaje y bajada;
 *     {@code null} si la ruta no tiene trazado cargado
 * @param boardingPoint donde tomar la buseta; {@code null} sin trazado
 * @param dropOffPoint donde bajarse; {@code null} sin trazado
 */
public record RouteSearchResult(Route route, Duration estimatedTime, Double travelDistanceInMeters,
                                BoardingPoint boardingPoint, BoardingPoint dropOffPoint) {

    /** Resultado con red: la mejor es la mas rapida segun Google. */
    public static RouteSearchResult online(Route route, Duration estimatedTime,
                                           BoardingPoint boarding, BoardingPoint dropOff,
                                           Double travelDistanceInMeters) {
        return new RouteSearchResult(route, estimatedTime, travelDistanceInMeters, boarding, dropOff);
    }

    /**
     * Resultado sin red: la mejor es la de menor recorrido, y no hay tiempo que dar.
     * {@code travelDistanceInMeters} queda en {@code null} si la ruta ganadora no tiene
     * trazado, que es distinto de un recorrido de cero metros.
     */
    public static RouteSearchResult offline(Route route, Double travelDistanceInMeters,
                                            BoardingPoint boarding, BoardingPoint dropOff) {
        return new RouteSearchResult(route, null, travelDistanceInMeters, boarding, dropOff);
    }

    /** Tiempo en buseta, vacio en modo offline. */
    public Optional<Duration> travelTime() {
        return Optional.ofNullable(estimatedTime);
    }

    /** Recorrido en buseta, vacio si la ruta todavia no tiene trazado. */
    public OptionalDouble travelDistance() {
        return travelDistanceInMeters == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(travelDistanceInMeters);
    }

    /** Donde tomar la buseta, vacio si la ruta todavia no tiene trazado. */
    public Optional<BoardingPoint> boarding() {
        return Optional.ofNullable(boardingPoint);
    }

    /** Donde bajarse, vacio si la ruta todavia no tiene trazado. */
    public Optional<BoardingPoint> dropOff() {
        return Optional.ofNullable(dropOffPoint);
    }
}
