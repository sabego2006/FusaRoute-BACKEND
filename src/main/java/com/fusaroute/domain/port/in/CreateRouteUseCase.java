package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.RouteDraft;

/** Crea una ruta ACTIVA (RF-11). */
public interface CreateRouteUseCase {

    AdminRoute create(RouteDraft draft);
}
