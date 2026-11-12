package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.AdminRoute;

/** Detalle de una ruta en cualquier estado (administrador, RF-11). */
public interface GetAdminRouteUseCase {

    AdminRoute getById(Long id);
}
