package com.fusaroute.domain.model;

/**
 * Una ruta que sirve a un par origen-destino: donde se aborda, donde se baja y cuanto se viaja entre
 * los dos. El abordaje queda ANTES que la bajada en el recorrido (una ruta = un sentido).
 */
public record RouteMatch(Route route, BoardingPoint boarding, BoardingPoint dropOff) {

    /** Metros sobre el trazado entre abordaje y bajada. */
    public double rideDistanceInMeters() {
        return dropOff.distanceAlongPathInMeters() - boarding.distanceAlongPathInMeters();
    }

    /** Caminata total aproximada (ambas puntas, con el factor de rodeo). */
    public double walkingDistanceInMeters() {
        return boarding.walkingDistanceInMeters() + dropOff.walkingDistanceInMeters();
    }

    /** Tramo del trazado que recorre la buseta, de abordaje a bajada. */
    public java.util.List<Coordinate> ridePath() {
        return route.pathBetween(boarding.distanceAlongPathInMeters(), dropOff.distanceAlongPathInMeters());
    }

    /**
     * Barrios que la buseta pasa entre el abordaje y la bajada (SCRUM-198), que son los unicos que
     * el resultado de la busqueda debe nombrar. Ver {@link Route#neighborhoodsBetween}.
     */
    public java.util.List<String> tripNeighborhoods() {
        return route.neighborhoodsBetween(
                boarding.distanceAlongPathInMeters(), dropOff.distanceAlongPathInMeters());
    }
}
