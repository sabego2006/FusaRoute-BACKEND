package com.fusaroute.domain.model;

/**
 * Estado de una ruta. SUSPENDIDA es una suspension temporal; INACTIVA es el "borrado"
 * del administrador (soft delete, RF-11). Ninguna de las dos se publica en el catalogo.
 */
public enum RouteStatus {
    ACTIVA,
    SUSPENDIDA,
    INACTIVA
}
