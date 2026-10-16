package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.GetRouteFareUseCase.RouteFare;

import java.time.LocalDate;
import java.util.List;

/** Costo del pasaje de una ruta: solo las tarifas vigentes y su fecha de actualizacion. */
public record RouteFareResponse(Long id, String name, String type, List<FareResponse> fares, LocalDate lastUpdatedAt) {

    static RouteFareResponse from(RouteFare routeFare) {
        return new RouteFareResponse(
                routeFare.id(),
                routeFare.name(),
                routeFare.type().name(),
                routeFare.fares().stream().map(FareResponse::from).toList(),
                routeFare.lastUpdatedAt());
    }
}
