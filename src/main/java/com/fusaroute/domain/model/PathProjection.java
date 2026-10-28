package com.fusaroute.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * Proyeccion de un punto sobre el trazado de una ruta (RF-04, ultima milla).
 *
 * El punto mas cercano casi nunca es un vertice del GeoJSON: lo normal es que caiga
 * en mitad de un tramo recto entre dos vertices. Por eso se proyecta sobre cada
 * segmento y no solo se mide contra los vertices — medir contra vertices daria
 * distancias infladas en tramos largos y descartaria rutas que si sirven.
 *
 * Para proyectar se trabaja en un plano local en vez de sobre la esfera: a las
 * distancias de la ultima milla (cientos de metros) la curvatura es despreciable y
 * la formula cerrada del plano es exacta de sobra. La distancia final si se mide con
 * haversine, que es la que se compara contra el umbral de 800 m.
 */
final class PathProjection {

    private PathProjection() {
    }

    /**
     * Punto del trazado mas cercano a {@code from}.
     *
     * @return vacio si la ruta no tiene trazado cargado. No es lo mismo que "queda
     *     lejos": una ruta sin GeoJSON sigue siendo buscable, solo que sin ultima
     *     milla. Quien decide descartar por distancia es
     *     {@link BoardingPoint#isWithinWalkingDistance()}.
     */
    static Optional<BoardingPoint> nearestPointOn(List<Coordinate> path, Coordinate from) {
        if (path.isEmpty()) {
            return Optional.empty();
        }
        if (path.size() == 1) {
            Coordinate only = path.get(0);
            return Optional.of(new BoardingPoint(only, from.distanceInMetersTo(only)));
        }

        BoardingPoint nearest = null;
        for (int i = 0; i < path.size() - 1; i++) {
            BoardingPoint candidate = nearestPointOnSegment(path.get(i), path.get(i + 1), from);
            if (nearest == null || candidate.distanceInMeters() < nearest.distanceInMeters()) {
                nearest = candidate;
            }
        }
        return Optional.of(nearest);
    }

    private static BoardingPoint nearestPointOnSegment(Coordinate start, Coordinate end, Coordinate from) {
        double ratio = projectionRatio(start, end, from);
        Coordinate projected = interpolate(start, end, ratio);
        return new BoardingPoint(projected, from.distanceInMetersTo(projected));
    }

    /**
     * Posicion del pie de la perpendicular dentro del segmento, en [0, 1]. Se recorta
     * a ese rango a proposito: si la perpendicular cae fuera del tramo, el punto mas
     * cercano del tramo es uno de sus extremos.
     */
    private static double projectionRatio(Coordinate start, Coordinate end, Coordinate from) {
        // Un grado de longitud se acorta con la latitud; sin este factor la proyeccion
        // se deforma y el pie de la perpendicular cae corrido hacia el este u oeste.
        double lonScale = Math.cos(Math.toRadians(from.latitude()));

        double startX = (start.longitude() - from.longitude()) * lonScale;
        double startY = start.latitude() - from.latitude();
        double endX = (end.longitude() - from.longitude()) * lonScale;
        double endY = end.latitude() - from.latitude();

        double segmentX = endX - startX;
        double segmentY = endY - startY;
        double segmentLengthSquared = segmentX * segmentX + segmentY * segmentY;
        if (segmentLengthSquared == 0) {
            // Vertice repetido en el GeoJSON: el segmento es un punto.
            return 0;
        }

        // El usuario esta en el origen, asi que el vector hacia el es -start.
        double ratio = -(startX * segmentX + startY * segmentY) / segmentLengthSquared;
        return Math.clamp(ratio, 0d, 1d);
    }

    private static Coordinate interpolate(Coordinate start, Coordinate end, double ratio) {
        return new Coordinate(
                start.latitude() + ratio * (end.latitude() - start.latitude()),
                start.longitude() + ratio * (end.longitude() - start.longitude()));
    }
}
