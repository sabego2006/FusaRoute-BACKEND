package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Route;

import java.util.List;

/** Puerto de entrada del catalogo publico de rutas (RF-15). */
public interface ListActiveRoutesUseCase {

    /** Rutas activas ordenadas por nombre, cada una con solo sus tarifas vigentes. */
    List<Route> listActive();
}
