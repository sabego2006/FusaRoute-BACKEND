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
 * @param distanceAlongPathInMeters cuanto trazado queda detras de este punto, medido
 *     desde el inicio del recorrido. Es lo que permite saber cuanto hay que viajar en
 *     buseta entre dos puntos de la misma ruta (modo offline, RF-04)
 */
public record BoardingPoint(Coordinate point, double distanceInMeters, double distanceAlongPathInMeters) {

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

    /**
     * Caminata ampliada (P1): si ninguna ruta cumple {@link #MAX_WALKING_DISTANCE_METERS} se ofrece la
     * mejor con caminata hasta aqui, marcada {@code longWalk}. Se mide, igual que el umbral normal,
     * sobre la distancia en linea recta al trazado.
     */
    public static final double LONG_WALK_DISTANCE_METERS = 1_500;

    /**
     * Las calles no van en linea recta: la caminata real que se le muestra al usuario es la distancia
     * recta por este factor (decision de Santiago, 2026-11-08), siempre rotulada "aprox.".
     */
    public static final double WALKING_DETOUR_FACTOR = 1.3;

    /** Caminata aproximada que se le muestra al usuario: linea recta por {@link #WALKING_DETOUR_FACTOR}. */
    public double walkingDistanceInMeters() {
        return distanceInMeters * WALKING_DETOUR_FACTOR;
    }

    /** Minutos a pie (5 km/h) sobre {@link #walkingDistanceInMeters()}, redondeados hacia arriba. */
    public long walkingMinutesCeil() {
        return (long) Math.ceil(walkingDistanceInMeters() / (WALKING_SPEED_METERS_PER_SECOND * 60));
    }

    /** Tiempo a pie sobre {@link #walkingDistanceInMeters()}; es el que entra al ranking puerta a puerta. */
    public Duration approxWalkingTime() {
        return Duration.ofSeconds(Math.round(walkingDistanceInMeters() / WALKING_SPEED_METERS_PER_SECOND));
    }

    /** Si es false, la ruta se descarta como candidata. */
    public boolean isWithinWalkingDistance() {
        return distanceInMeters <= MAX_WALKING_DISTANCE_METERS;
    }
}
