package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.AdminRoute;

import java.util.List;

/** Todas las rutas, en cualquier estado, ordenadas por nombre (administrador, RF-11). */
public interface ListAllRoutesUseCase {

    List<AdminRoute> listAll();
}
