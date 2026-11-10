package com.fusaroute.domain.port.in;

/** Lo que llega al tocar "Ya me subi". {@code destinationPlaceId} es opcional. */
public record RecordRideCommand(Long userId, Long routeId, String destinationLabel, Long destinationPlaceId,
                                Double destinationLat, Double destinationLng, String originLabel) {
}
