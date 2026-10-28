package com.fusaroute.domain.model;

/**
 * Objeto de valor que representa una coordenada geográfica.
 */
public record Coordinate(double latitude, double longitude) {

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
