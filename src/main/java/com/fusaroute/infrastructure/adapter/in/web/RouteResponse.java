package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Route;

import java.util.List;

/**
 * Ruta del catalogo publico: barrios en orden de recorrido y solo las tarifas vigentes.
 *
 * @param signFile nombre del archivo del letrero, que el frontend resuelve a
 *     {@code public/letreros/<signFile>.svg} (SCRUM-184); {@code null} si la ruta no
 *     tiene letrero fotografiado, y entonces no se pinta nada — ni un hueco ni un
 *     letrero inventado. Va en el listado y no solo en el detalle porque el catalogo
 *     tambien muestra el letrero, y son 140 caracteres como maximo: no es el trazado,
 *     que si se queda fuera del listado por peso.
 */
public record RouteResponse(Long id, String name, String signFile, String type,
                            List<String> neighborhoods, List<FareResponse> fares) {

    static RouteResponse from(Route route) {
        return new RouteResponse(
                route.id(),
                route.name(),
                route.signFile(),
                route.type().name(),
                route.neighborhoods(),
                route.fares().stream().map(FareResponse::from).toList());
    }
}
