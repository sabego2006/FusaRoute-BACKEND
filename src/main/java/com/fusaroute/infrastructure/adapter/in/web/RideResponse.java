package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Ride;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Un viaje del historial. Sin {@code userId}: es "mis viajes". La fecha sale con el
 * desfase de Bogota (-05:00), que es como la lee quien usa la app.
 */
public record RideResponse(Long id, Long routeId, String routeName, String signFile,
                           String destinationLabel, Long destinationPlaceId,
                           double destinationLat, double destinationLng,
                           String originLabel, OffsetDateTime boardedAt) {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public static RideResponse from(Ride ride) {
        return new RideResponse(ride.id(), ride.routeId(), ride.routeName(), ride.signFile(),
                ride.destinationLabel(), ride.destinationPlaceId(),
                ride.destination().latitude(), ride.destination().longitude(),
                ride.originLabel(), ride.boardedAt().atZone(BOGOTA).toOffsetDateTime());
    }
}
