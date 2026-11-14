package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.AdminMetrics;

/**
 * Panel de metricas de uso del administrador (RF-13, SCRUM-26): exactamente cinco metricas, calculadas en el
 * momento de pedirse. Solo administrador: lo exige {@code SecurityConfig} sobre {@code /api/admin/**}.
 *
 * <p><b>Desviacion respecto de RF-13:</b> la historia pide las 5 rutas mas consultadas del historial
 * acumulado, pero {@code search_history} no guarda la ruta y {@code search_events} se purga a 30 dias. Por eso
 * el ranking sale de {@code search_events}: eventos {@code SEARCH} con ruta, de los ultimos
 * {@value #TOP_ROUTES_WINDOW_DAYS} dias. Ver {@code docs/contratos/rf13-metricas.md}.
 */
public interface GetAdminMetricsUseCase {

    /** Dias que mira el ranking de rutas: lo que la purga de {@code search_events} deja disponible. */
    int TOP_ROUTES_WINDOW_DAYS = 30;

    /** Tamano maximo del ranking. */
    int TOP_ROUTES_LIMIT = 5;

    /** Zona con la que se decide cual es "el dia de hoy". */
    String TIMEZONE = "America/Bogota";

    AdminMetrics getMetrics();
}
