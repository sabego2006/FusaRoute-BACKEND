package com.fusaroute.domain.port.in;

/**
 * Registra una busqueda en el historial del usuario (RF-07, SCRUM-20).
 *
 * Va aparte de {@link SearchRouteUseCase} a proposito: buscar es publico y
 * funciona sin sesion (RF-04), mientras que el historial solo existe para un
 * usuario autenticado. Mezclarlos obligaria a la busqueda a conocer la sesion.
 */
public interface RecordSearchUseCase {

    /**
     * Anota la busqueda y recorta el historial al tope de RF-07, descartando la
     * mas antigua si hiciera falta.
     */
    void record(Long userId, String origin, String destination);
}
