package com.fusaroute.domain.model;

import java.time.Instant;

/**
 * Aviso in-app de una ruta suspendida (RF-12). No se guarda: se calcula al leer, solo para suspensiones
 * vigentes. Hoy hay un unico tipo, {@value #ROUTE_SUSPENDED}.
 */
public record UserNotification(String type, Long suspensionId, Long routeId, String routeName,
                               Instant startsAt, Instant endsAt, String reason, NotificationReason because) {

    public static final String ROUTE_SUSPENDED = "ROUTE_SUSPENDED";

    public static UserNotification routeSuspended(RouteSuspension suspension, NotificationReason because) {
        return new UserNotification(ROUTE_SUSPENDED, suspension.id(), suspension.routeId(), suspension.routeName(),
                suspension.startsAt(), suspension.endsAt(), suspension.reason(), because);
    }
}
