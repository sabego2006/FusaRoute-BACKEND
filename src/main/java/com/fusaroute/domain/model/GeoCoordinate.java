package com.fusaroute.domain.model;

public record GeoCoordinate(double latitude, double longitude) {

    public GeoCoordinate {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude fuera de rango [-90, 90]: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude fuera de rango [-180, 180]: " + longitude);
        }
    }
}
