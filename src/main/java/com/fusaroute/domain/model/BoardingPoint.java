package com.fusaroute.domain.model;

import java.time.Duration;

/**
 * Punto del trazado de una ruta mas cercano a pie desde un origen (abordaje) o hacia
 * un destino (bajada), con la distancia que hay que caminar hasta el — la "ultima
 * milla" de RF-04.
 *
 * En Fusagasuga no hay paradas formales: uno para la buseta con la mano, asi que el
 * punto de abordaje no es una parada del catalogo sino la proyeccion del usuario
 * sobre el trazado. Por eso es un valor calculado y no una entidad.
 *
 * @param point coordenada sobre el trazado, no necesariamente un vertice del GeoJSON
 * @param distanceInMeters distancia geometrica desde el punto del usuario, en metros
 */
public record BoardingPoint(Coordinate point, double distanceInMeters) {

    /**
     * Mas alla de esta distancia la ruta deja de ser candidata (RF-04): obliga a una
     * caminata que ya no compensa, aunque la buseta pase "cerca" en el mapa.
     */
    public static final double MAX_WALKING_DISTANCE_METERS = 800;

    /** 5 km/h es el paso de un adulto; la historia fija esta velocidad explicitamente. */
    private static final double WALKING_SPEED_METERS_PER_SECOND = 5_000d / 3_600d;

    public BoardingPoint {
        if (distanceInMeters < 0) {
            throw new IllegalArgumentException("La distancia a pie no puede ser negativa: " + distanceInMeters);
        }
    }

    /**
     * Tiempo aproximado de la caminata a 5 km/h. Aproximado a proposito: es distancia
     * en linea recta, sin ruteo peatonal.
     */
    public Duration walkingTime() {
        return Duration.ofSeconds(Math.round(distanceInMeters / WALKING_SPEED_METERS_PER_SECOND));
    }

    /** Si es false, la ruta se descarta como candidata. */
    public boolean isWithinWalkingDistance() {
        return distanceInMeters <= MAX_WALKING_DISTANCE_METERS;
    }
}
