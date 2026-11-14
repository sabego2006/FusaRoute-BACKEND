package com.fusaroute.domain.model;

/**
 * Por que un usuario recibe el aviso de una ruta suspendida. La definicion de cada motivo es una decision
 * de producto pendiente de aprobacion de la PO: {@code docs/decisiones/rf12-destinatarios-del-aviso.md}.
 */
public enum NotificationReason {
    /** La ruta sirve el destino favorito del usuario. */
    FAVORITE,
    /** La ruta esta en al menos 3 de los ultimos 6 viajes del usuario. */
    FREQUENT
}
