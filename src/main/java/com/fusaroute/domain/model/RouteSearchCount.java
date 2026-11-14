package com.fusaroute.domain.model;

/**
 * Cuantas busquedas terminaron en una ruta, tal como las agrupa la base (SCRUM-26). Todavia no sabe el
 * nombre ni si la ruta existe: eso lo resuelve el caso de uso.
 */
public record RouteSearchCount(Long routeId, long searches) {
}
