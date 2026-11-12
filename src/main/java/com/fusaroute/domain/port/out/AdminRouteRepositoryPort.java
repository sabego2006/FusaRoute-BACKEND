package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.FareChange;
import com.fusaroute.domain.model.RouteDraft;
import com.fusaroute.domain.model.RouteStatus;

import java.util.List;
import java.util.Optional;

/**
 * Almacen de rutas para el administrador: lee en cualquier estado y escribe. El
 * catalogo publico sigue usando {@link RouteRepositoryPort}, que es de solo lectura.
 * Las tarifas de {@link AdminRoute} son todas las filas, sin filtrar por vigencia.
 */
public interface AdminRouteRepositoryPort {

    List<AdminRoute> findAll();

    Optional<AdminRoute> findById(Long id);

    /** {@code excludeId} permite ignorar la propia ruta al modificarla; puede ser nulo. */
    boolean existsByName(String name, Long excludeId);

    boolean existsBySignFile(String signFile, Long excludeId);

    /** Crea la ruta ACTIVA con sus barrios y sus filas de tarifa. */
    AdminRoute create(RouteDraft draft, List<Fare> fares);

    /** Reemplaza los datos de la ruta; las tarifas solo se tocan si {@code fareChange.changed()}. */
    AdminRoute update(Long id, RouteDraft draft, FareChange fareChange);

    void setStatus(Long id, RouteStatus status);
}
