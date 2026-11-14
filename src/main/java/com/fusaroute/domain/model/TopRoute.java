package com.fusaroute.domain.model;

/**
 * Una fila del ranking de rutas mas consultadas del panel de RF-13 (SCRUM-26).
 *
 * @param routeId   id de la ruta; la ruta existe (las que ya no existen se omiten del ranking).
 * @param routeName nombre actual de la ruta, aunque este INACTIVA o suspendida: el ranking es historico.
 * @param searches  busquedas que terminaron en esa ruta dentro de la ventana del ranking.
 */
public record TopRoute(Long routeId, String routeName, long searches) {
}
