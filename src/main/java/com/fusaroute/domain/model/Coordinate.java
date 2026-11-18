package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.InvalidCoordinateException;

/**
 * Objeto de valor que representa una coordenada geográfica.
 */
public record Coordinate(double latitude, double longitude) {

    /**
     * Rechaza coordenadas fuera del rango WGS-84, NaN e infinito. Se valida aqui, en el
     * dominio, para que ningun adaptador (HTTP, GeoJSON de la base) construya un punto
     * imposible que luego llegue sin error a Google Directions o al calculo de distancia.
     */
    public Coordinate {
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new InvalidCoordinateException("Latitud fuera de rango [-90, 90]: " + latitude);
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new InvalidCoordinateException("Longitud fuera de rango [-180, 180]: " + longitude);
        }
    }

    /** Radio medio terrestre (WGS-84), en metros. */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    /** Por debajo de esta distancia los dos puntos son el mismo sitio y no hay rumbo que dar. */
    public static final double MIN_BEARING_DISTANCE_METERS = 20;

    /**
     * Rumbo INICIAL hacia {@code other}, en grados desde el norte y en sentido horario
     * (0 = norte, 90 = oriente, 180 = sur, 270 = occidente).
     *
     * Es un dato geometrico, no una frase: quien lo muestra decide con que palabra lo dice
     * ("hacia el nororiente"). Es el rumbo del circulo maximo y no el de una calle, asi que
     * orienta pero no reemplaza mirar el mapa; a las distancias de la ultima milla la diferencia
     * con la loxodromica es de centesimas de grado.
     *
     * @return vacio si los dos puntos estan a menos de {@link #MIN_BEARING_DISTANCE_METERS}:
     *     ahi no hay una direccion que tenga sentido dar, y devolver una seria inventar precision
     */
    public java.util.OptionalDouble bearingDegreesTo(Coordinate other) {
        if (distanceInMetersTo(other) < MIN_BEARING_DISTANCE_METERS) {
            return java.util.OptionalDouble.empty();
        }
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double lonDelta = Math.toRadians(other.longitude - longitude);
        double y = Math.sin(lonDelta) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lonDelta);
        return java.util.OptionalDouble.of((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    /**
     * Distancia en linea recta sobre la superficie terrestre, en metros (haversine).
     *
     * Es distancia geometrica, no caminando: no conoce calles ni sentidos. Alcanza
     * para la ultima milla (RF-04) porque ahi solo se compara contra un umbral de
     * 800 m y se estima el tiempo a pie; medir el recorrido peatonal real exigiria
     * ruteo de Google, que esa historia descarta explicitamente.
     */
    public double distanceInMetersTo(Coordinate other) {
        double latDelta = Math.toRadians(other.latitude - latitude);
        double lonDelta = Math.toRadians(other.longitude - longitude);
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);

        double a = Math.pow(Math.sin(latDelta / 2), 2)
                + Math.pow(Math.sin(lonDelta / 2), 2) * Math.cos(lat1) * Math.cos(lat2);

        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
