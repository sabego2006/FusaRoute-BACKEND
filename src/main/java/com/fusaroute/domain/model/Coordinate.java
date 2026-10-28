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
