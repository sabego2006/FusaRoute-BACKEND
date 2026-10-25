package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.RouteSearchResult;

/** Resultado de GET /api/routes/search: la ruta sugerida y el tiempo estimado para recorrerla. */
public record RouteSearchResponse(RouteResponse route, long estimatedMinutes) {

    static RouteSearchResponse from(RouteSearchResult result) {
        return new RouteSearchResponse(RouteResponse.from(result.route()), result.estimatedTime().toMinutes());
    }
}
