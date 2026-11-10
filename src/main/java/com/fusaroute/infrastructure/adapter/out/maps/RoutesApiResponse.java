package com.fusaroute.infrastructure.adapter.out.maps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Respuesta de {@code POST /directions/v2:computeRoutes} (Google Routes API) con la field mask
 * {@code routes.duration,routes.distanceMeters}. Solo lo que se lee: la duracion llega como texto en
 * segundos ({@code "1234s"}, a veces con decimales) y no como numero.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RoutesApiResponse(List<Route> routes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(String duration, Long distanceMeters) {
    }
}
