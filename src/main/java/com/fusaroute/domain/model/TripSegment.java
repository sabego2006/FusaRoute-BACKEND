package com.fusaroute.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * El tramo que la persona recorre de verdad (SCRUM-198), para que el resultado de la busqueda no le
 * muestre la ruta completa: el pedazo de trazado entre la subida y la bajada, los barrios que quedan
 * dentro de ese pedazo, y como se explica cada caminata.
 *
 * Es un valor calculado por la busqueda y no un dato guardado: depende del par origen-destino, no de
 * la ruta. Una ruta sin trazado no tiene tramo — ahi no se sabe donde sube ni donde baja, y el
 * resultado lo dice en vez de inventarlo.
 *
 * @param ridePath trazado recortado entre subida y bajada; nunca la ruta completa. Vacio si la ruta
 *     no tiene trazado
 * @param neighborhoods barrios dentro del tramo, en orden de recorrido. Vacia es un resultado valido
 *     y frecuente (ver {@link Route#neighborhoodsBetween})
 * @param boardingWalk como se explica la caminata hasta la subida
 * @param dropOffWalk como se explica la caminata desde la bajada
 */
public record TripSegment(List<Coordinate> ridePath, List<String> neighborhoods,
                          WalkGuide boardingWalk, WalkGuide dropOffWalk) {

    public TripSegment {
        ridePath = ridePath == null ? List.of() : List.copyOf(ridePath);
        neighborhoods = neighborhoods == null ? List.of() : List.copyOf(neighborhoods);
    }

    /**
     * Lo que hace entendible una caminata: hacia donde va y hasta que sitio reconocible llega.
     *
     * Los dos datos pueden faltar por separado y por razones distintas, asi que van como opcionales
     * y no como cadenas vacias: sin rumbo cuando el punto esta tan cerca que no hay direccion que
     * dar, y sin referencia cuando ningun lugar del catalogo queda lo bastante cerca para nombrarlo.
     *
     * @param bearingDegrees rumbo en grados desde el norte (ver {@link Coordinate#bearingDegreesTo});
     *     {@code null} si no hay
     * @param reference nombre del lugar del catalogo mas cercano al punto; {@code null} si ninguno
     *     queda a menos de {@link #REFERENCE_RADIUS_METERS}
     */
    public record WalkGuide(Double bearingDegrees, String reference) {

        /** Sin rumbo ni referencia: lo que queda cuando no hay con que orientar la caminata. */
        public static final WalkGuide NONE = new WalkGuide(null, null);

        public OptionalDouble bearing() {
            return bearingDegrees == null ? OptionalDouble.empty() : OptionalDouble.of(bearingDegrees);
        }

        public Optional<String> findReference() {
            return Optional.ofNullable(reference);
        }
    }

    /**
     * Mas lejos de aqui, nombrar un lugar como referencia de la caminata manda a la persona a otra
     * esquina en vez de orientarla, asi que se prefiere no nombrar ninguno. Es del orden de una o dos
     * cuadras de Fusagasuga.
     */
    public static final double REFERENCE_RADIUS_METERS = 300;

    /**
     * El tramo de una ruta sin trazado: no se sabe nada de el. Existe para que quien lo muestre no
     * tenga que distinguir entre "no hay tramo" y "tramo vacio" — en los dos casos hay que decir con
     * honestidad que no hay detalle, y nunca dibujar la ruta completa como si fuera el viaje.
     */
    public static final TripSegment UNKNOWN =
            new TripSegment(List.of(), List.of(), WalkGuide.NONE, WalkGuide.NONE);

    /** Si hay pedazo de trazado que dibujar. Falso en una ruta sin trazado. */
    public boolean hasRidePath() {
        return ridePath.size() >= 2;
    }
}
