package com.fusaroute.domain.model;

import java.time.Duration;

/**
 * Resultado de una búsqueda de ruta (RF-04): la ruta elegida como mejor opción y el
 * tiempo estimado para recorrerla entre el origen y el destino solicitados.
 */
public record RouteSearchResult(Route route, Duration estimatedTime) {
}
