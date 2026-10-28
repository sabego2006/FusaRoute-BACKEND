package com.fusaroute.domain.model;

/**
 * Rol de la cuenta. USER es el usuario final; ADMIN administra el catalogo de
 * rutas (RF-01/02/03). El registro publico siempre crea un USER.
 */
public enum UserRole {
    USER,
    ADMIN
}
