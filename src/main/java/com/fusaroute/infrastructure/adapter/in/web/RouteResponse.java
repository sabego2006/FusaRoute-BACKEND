package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Route;

import java.util.List;

/** Ruta del catalogo publico: barrios en orden de recorrido y solo las tarifas vigentes. */
public record RouteResponse(Long id, String name, String type, List<String> neighborhoods, List<FareResponse> fares) {

    static RouteResponse from(Route route) {
        return new RouteResponse(
                route.id(),
                route.name(),
                route.type().name(),
                route.neighborhoods(),
                route.fares().stream().map(FareResponse::from).toList());
    }
}
