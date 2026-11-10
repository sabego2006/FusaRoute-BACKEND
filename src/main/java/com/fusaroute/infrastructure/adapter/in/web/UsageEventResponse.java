package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.UsageEvent;

import java.time.Instant;

/** Una fila de {@code search_events} en JSON (SCRUM-186). Sin IP ni usuario, como la tabla. */
public record UsageEventResponse(
        Long id,
        Instant ts,
        String eventType,
        String originLabel,
        String destinationLabel,
        Long routeId,
        String outcome,
        String etaSource,
        Integer latencyMs,
        boolean congestion) {

    public static UsageEventResponse from(UsageEvent event) {
        return new UsageEventResponse(event.id(), event.occurredAt(), event.type().name(),
                event.originLabel(), event.destinationLabel(), event.routeId(), event.outcome().name(),
                event.etaSource() == null ? null : event.etaSource().name(),
                event.latencyMs(), event.congestion());
    }
}
