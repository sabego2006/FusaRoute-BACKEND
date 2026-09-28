package com.fusaroute.domain.model;

/**
 * Tipo de ruta. Determina la forma de la tarifa: la urbana tiene un precio unico
 * y la intermunicipal una tabla por punto de referencia de bajada.
 */
public enum RouteType {
    URBANA,
    INTERMUNICIPAL
}
