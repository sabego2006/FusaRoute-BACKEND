package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.AdminRoute;

/** Devuelve a ACTIVA una ruta que estaba INACTIVA (RF-11). */
public interface RestoreRouteUseCase {

    AdminRoute restore(Long id);
}
