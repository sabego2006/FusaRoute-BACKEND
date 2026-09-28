package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.Route;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida hacia el almacen de rutas. Devuelve las rutas completas, con
 * todas las filas de tarifa: filtrar por estado y elegir la tarifa vigente son
 * decisiones del dominio, no de la consulta.
 */
public interface RouteRepositoryPort {

    List<Route> findAll();

    Optional<Route> findById(Long id);
}
