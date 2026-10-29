package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.RouteSign;

import java.util.List;

/** Acceso a los letreros fisicos ligados a una ruta (SCRUM-176). */
public interface RouteSignRepositoryPort {

    /**
     * Letreros de una ruta, en el orden en que se registraron.
     *
     * @return lista vacia si la ruta no tiene ningun letrero con evidencia de campo
     *     todavia — no es un error, es el estado real de la mayoria de las rutas.
     */
    List<RouteSign> findByRouteId(Long routeId);
}
