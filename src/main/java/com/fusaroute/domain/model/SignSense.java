package com.fusaroute.domain.model;

/**
 * Sentido de recorrido que documenta un letrero (SCRUM-176). Solo las tablas de
 * franjas (TABLA_FRANJAS) lo traen: una buseta urbana muestra una tabla distinta
 * segun si va hacia el centro o se aleja de el.
 *
 * {@code null} en {@link RouteSign#sense()} significa que el letrero no documenta
 * sentido — la mayoria de los casos, porque la placa en arco es la misma yendo y
 * viniendo.
 */
public enum SignSense {
    SUBIENDO,
    BAJANDO
}
