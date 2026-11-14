package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.SuspensionView;

import java.time.Instant;

/**
 * Una suspension de ruta tal como la ve el administrador (RF-12). Los instantes salen en ISO-8601 UTC.
 *
 * @param state {@code ACTIVE}, {@code UPCOMING}, {@code ENDED} o {@code LIFTED}, calculado al responder
 * @param reason {@code null} si no se dio motivo
 * @param liftedAt {@code null} mientras nadie la haya levantado a mano
 */
public record SuspensionResponse(Long id, Long routeId, String routeName, Instant startsAt, Instant endsAt,
                                 String reason, Instant liftedAt, String state) {

    static SuspensionResponse from(SuspensionView view) {
        RouteSuspension s = view.suspension();
        return new SuspensionResponse(s.id(), s.routeId(), s.routeName(), s.startsAt(), s.endsAt(), s.reason(),
                s.liftedAt(), view.state().name());
    }
}
