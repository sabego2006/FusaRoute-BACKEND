package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Route;

/** Puerto de entrada del detalle de una ruta del catalogo publico (RF-15). */
public interface GetRouteDetailUseCase {

    /**
     * @throws com.fusaroute.domain.exception.RouteNotFoundException si la ruta no
     *     existe o esta suspendida.
     */
    Route getById(Long id);
}
