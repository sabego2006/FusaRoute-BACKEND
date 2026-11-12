package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.RouteDraft;

/** Reemplaza los datos de una ruta; el estado no cambia (RF-11). */
public interface UpdateRouteUseCase {

    AdminRoute update(Long id, RouteDraft draft);
}
