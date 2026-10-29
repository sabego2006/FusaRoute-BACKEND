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
            return Optional.of(new BoardingPoint(only, from.distanceInMetersTo(only), 0));
        }

        BoardingPoint nearest = null;
        double traversed = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Coordinate start = path.get(i);
            Coordinate end = path.get(i + 1);
            double segmentLength = start.distanceInMetersTo(end);

            double ratio = projectionRatio(start, end, from);
            Coordinate projected = interpolate(start, end, ratio);
            double distance = from.distanceInMetersTo(projected);

            if (nearest == null || distance < nearest.distanceInMeters()) {
                nearest = new BoardingPoint(projected, distance, traversed + ratio * segmentLength);
            }
            traversed += segmentLength;
        }
        return Optional.of(nearest);
    }

    /**
     * Punto del trazado a una fraccion [0, 1] de su longitud total, midiendo por la
     * distancia real acumulada entre vertices y no por indice de vertice: un tramo
     * largo entre dos vertices no debe pesar lo mismo que uno corto.
     *
     * Existe para {@link Route#pointAtNeighborhood(String)}: cuando el cliente no
     * manda coordenadas propias (el selector de barrio solo da un nombre), es la unica
     * forma de ubicar un punto aproximado sobre el trazado sin pedirle a nadie que
     * camine la ruta con un GPS.
     *
     * @param path trazado con al menos 2 puntos; se asume ya validado por el llamador
     * @param fraction posicion relativa dentro del recorrido; se recorta a [0, 1]
     */
    static Coordinate pointAtFraction(List<Coordinate> path, double fraction) {
        double clamped = Math.clamp(fraction, 0d, 1d);
        double totalLength = totalLength(path);
        if (totalLength == 0) {
            // Trazado degenerado (todos los vertices en el mismo punto): no hay nada
            // que recorrer, asi que cualquier fraccion cae en el mismo lugar.
            return path.get(0);
        }

        double target = clamped * totalLength;
        double traversed = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Coordinate start = path.get(i);
            Coordinate end = path.get(i + 1);
            double segmentLength = start.distanceInMetersTo(end);
            boolean esUltimoSegmento = i == path.size() - 2;

            if (traversed + segmentLength >= target || esUltimoSegmento) {
                double ratio = segmentLength == 0 ? 0 : (target - traversed) / segmentLength;
                return interpolate(start, end, Math.clamp(ratio, 0d, 1d));
            }
            traversed += segmentLength;
        }
        return path.get(path.size() - 1);
    }

    private static double totalLength(List<Coordinate> path) {
        double total = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += path.get(i).distanceInMetersTo(path.get(i + 1));
        }
        return total;
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
