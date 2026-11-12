package com.fusaroute.infrastructure.adapter.out.persistence;

import java.time.Instant;

/** Proyeccion de la lectura del historial: el viaje junto con el nombre y el letrero de su ruta. */
public interface RideHistoryRow {

    Long getId();

    Long getUserId();

    Long getRouteId();

    String getRouteName();

    String getSignFile();

    String getRouteStatus();

    String getDestinationLabel();

    Long getDestinationPlaceId();

    Double getDestinationLat();

    Double getDestinationLng();

    String getOriginLabel();

    Instant getBoardedAt();
}
