package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.UserNotification;

import java.time.Instant;

/**
 * Aviso in-app del usuario (RF-12). Hoy solo existe {@code type = "ROUTE_SUSPENDED"}.
 *
 * @param because {@code FAVORITE} o {@code FREQUENT}: por que le llega a este usuario
 */
public record UserNotificationResponse(String type, Long suspensionId, Long routeId, String routeName,
                                       Instant startsAt, Instant endsAt, String reason, String because) {

    static UserNotificationResponse from(UserNotification n) {
        return new UserNotificationResponse(n.type(), n.suspensionId(), n.routeId(), n.routeName(), n.startsAt(),
                n.endsAt(), n.reason(), n.because().name());
    }
}
