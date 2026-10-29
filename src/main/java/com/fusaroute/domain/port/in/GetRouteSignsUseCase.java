package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.RouteSign;

import java.util.List;

/** Letreros fisicos de una ruta (SCRUM-176), para que el pasajero reconozca la buseta. */
public interface GetRouteSignsUseCase {

    /**
     * @return letreros de la ruta en orden de registro; lista vacia si todavia no
     *     hay evidencia de campo que la respalde
     */
    List<RouteSign> forRoute(Long routeId);
}
