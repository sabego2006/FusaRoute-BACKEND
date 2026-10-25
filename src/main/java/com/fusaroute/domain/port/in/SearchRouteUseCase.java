package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.RouteSearchResult;

/**
 * Caso de uso para buscar la mejor ruta entre un origen y un destino.
 */
public interface SearchRouteUseCase {
    /**
     * Encuentra la ruta más rápida que conecte el barrio de origen con el de destino.
     *
     * @param originNeighborhood Nombre del barrio de origen.
     * @param originCoord Coordenadas exactas del punto de partida.
     * @param destNeighborhood Nombre del barrio de destino.
     * @param destCoord Coordenadas exactas del punto de llegada.
     * @return La ruta seleccionada como la mejor opción, junto con el tiempo estimado.
     * @throws com.fusaroute.domain.exception.NoRouteAvailableException si no hay rutas candidatas.
     */
    RouteSearchResult findBestRoute(String originNeighborhood, Coordinate originCoord,
                       String destNeighborhood, Coordinate destCoord);
}
