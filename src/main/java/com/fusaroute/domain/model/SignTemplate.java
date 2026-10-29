package com.fusaroute.domain.model;

/**
 * Forma fisica de una placa del letrero (SCRUM-175/176): arco con base, rectangulo
 * pequeño, o tabla de franjas de un solo sentido. Determina solo la forma, no el
 * texto ni el color — eso vive en {@link SignLine}.
 */
public enum SignTemplate {
    PLACA_ARCO,
    PLACA_RECT,
    TABLA_FRANJAS
}
