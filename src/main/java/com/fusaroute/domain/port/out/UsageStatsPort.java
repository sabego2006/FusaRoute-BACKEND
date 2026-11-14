package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.RouteSearchCount;

import java.time.Instant;
import java.util.List;

/**
 * Agregados de solo lectura sobre {@code search_events} para el panel de RF-13 (SCRUM-26). Cuentan en la
 * base; no traen filas a memoria. Solo cuentan eventos {@code SEARCH}: el toque de "Ya me subi"
 * ({@code BOARDED}) no es una busqueda.
 */
public interface UsageStatsPort {

    /**
     * Metrica 2: busquedas con {@code fromInclusive <= ts < toExclusive}, con cualquier resultado (una
     * busqueda sin ruta tambien es una busqueda).
     */
    long countSearchesBetween(Instant fromInclusive, Instant toExclusive);

    /**
     * Metrica 3, primera mitad: por cada ruta, las busquedas con ruta y {@code ts >= since}. Una fila por ruta
     * distinta, sin ordenar ni limitar: ordenar y recortar es regla del caso de uso, que antes descarta las
     * rutas que ya no existen.
     */
    List<RouteSearchCount> countSearchesByRoute(Instant since);
}
