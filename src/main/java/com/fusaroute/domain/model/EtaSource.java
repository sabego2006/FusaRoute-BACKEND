package com.fusaroute.domain.model;

/** De donde sale el tiempo en buseta que se le muestra al usuario. */
public enum EtaSource {
    /** Google Routes con trafico, para la ruta ganadora. */
    TRAFFIC,
    /** Estimado propio: recorrido sobre el trazado entre abordaje y bajada, dividido por la velocidad de la ruta. */
    ESTIMATED
}
