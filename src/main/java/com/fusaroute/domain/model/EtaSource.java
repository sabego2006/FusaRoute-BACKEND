package com.fusaroute.domain.model;

/** De donde salio el tiempo estimado de llegada que se le mostro al usuario. */
public enum EtaSource {
    /** Google Directions con trafico en tiempo real. */
    TRAFFIC,
    /** Estimacion propia a partir de la distancia, sin consultar trafico. */
    ESTIMATED
}
