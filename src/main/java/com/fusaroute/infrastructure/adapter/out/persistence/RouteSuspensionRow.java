package com.fusaroute.infrastructure.adapter.out.persistence;

import java.time.Instant;

/** Proyeccion de una suspension junto con el nombre de su ruta (JOIN a routes). */
public interface RouteSuspensionRow {

    Long getId();

    Long getRouteId();

    String getRouteName();

    Instant getStartsAt();

    Instant getEndsAt();

    String getReason();

    Instant getLiftedAt();
}
