package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.Coordinate;

import java.util.List;

/**
 * Aviso de posible congestion para un recorrido (RF-09).
 */
public interface GetCongestionWarningUseCase {

    /**
     * Sectores SIN circulo (cruce por nombre de barrio) congestionados en este momento.
     *
     * @param traversedNeighborhoods barrios por los que pasa el viaje. En el detalle de
     *     una ruta son todos los suyos; en una busqueda, solo los que quedan entre el
     *     origen y el destino — avisar de un trancon que el usuario ni siquiera va a
     *     atravesar seria ruido.
     * @return aviso vacio si ninguno esta en su franja ahora mismo
     */
    CongestionWarning forRoute(List<String> traversedNeighborhoods);

    /**
     * Como {@link #forRoute(List)} pero tambien evalua los sectores con CIRCULO (P1, V22): aplican si
     * el trazado recorrido pasa por el circulo. En el detalle de ruta, {@code traversedPath} es el
     * trazado completo.
     */
    CongestionWarning forRoute(List<String> traversedNeighborhoods, List<Coordinate> traversedPath);
}
