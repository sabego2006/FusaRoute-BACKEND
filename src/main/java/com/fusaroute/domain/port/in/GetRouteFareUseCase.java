package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.RouteType;

import java.time.LocalDate;
import java.util.List;

/** Puerto de entrada del costo del pasaje de una ruta del catalogo publico (RF-06). */
public interface GetRouteFareUseCase {

    /**
     * @throws com.fusaroute.domain.exception.RouteNotFoundException si la ruta no
     *     existe o esta suspendida.
     */
    RouteFare getByRouteId(Long id);

    /**
     * Vista del pasaje de una ruta: solo las tarifas vigentes y la fecha en que se
     * actualizo por ultima vez el precio.
     *
     * @param lastUpdatedAt vacio si la ruta no tiene ninguna tarifa vigente.
     */
    record RouteFare(Long id, String name, RouteType type, List<Fare> fares, LocalDate lastUpdatedAt) {
    }
}
