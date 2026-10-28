package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.BoardingPoint;

/**
 * Ultima milla de una punta del viaje (RF-04): donde queda el punto del trazado mas
 * cercano y cuanto hay que caminar hasta el.
 *
 * @param latitude latitud del punto sobre el trazado
 * @param longitude longitud del punto sobre el trazado
 * @param distanceInMeters distancia en linea recta, redondeada a metros
 * @param walkingMinutes tiempo aproximado a pie, a 5 km/h
 */
public record BoardingPointResponse(double latitude, double longitude,
                                    long distanceInMeters, long walkingMinutes) {

    static BoardingPointResponse from(BoardingPoint point) {
        return new BoardingPointResponse(
                point.point().latitude(),
                point.point().longitude(),
                Math.round(point.distanceInMeters()),
                point.walkingTime().toMinutes());
    }
}
