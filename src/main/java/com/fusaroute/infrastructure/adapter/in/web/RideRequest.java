package com.fusaroute.infrastructure.adapter.in.web;

/** Cuerpo de POST /api/users/me/rides. La validacion vive en el dominio ({@code Ride}). */
public record RideRequest(Long routeId, String destinationLabel, Long destinationPlaceId,
                          Double destinationLat, Double destinationLng, String originLabel) {
}
