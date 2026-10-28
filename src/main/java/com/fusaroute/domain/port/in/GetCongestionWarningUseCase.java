package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.CongestionWarning;

import java.util.List;

/**
 * Aviso de posible congestion para un recorrido (RF-09).
 */
public interface GetCongestionWarningUseCase {

    /**
     * Sectores congestionados que atraviesa el recorrido en este momento.
     *
     * @param traversedNeighborhoods barrios por los que pasa el viaje. En el detalle de
     *     una ruta son todos los suyos; en una busqueda, solo los que quedan entre el
     *     origen y el destino — avisar de un trancon que el usuario ni siquiera va a
     *     atravesar seria ruido.
     * @return aviso vacio si ninguno esta en su franja ahora mismo
     */
    CongestionWarning forRoute(List<String> traversedNeighborhoods);
}
