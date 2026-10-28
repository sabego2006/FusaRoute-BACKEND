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

    /**
     * Encuentra la ruta de menor recorrido, sin consultar a Google (RF-04, modo offline).
     *
     * Existe como operacion aparte y no como una variante de {@link #findBestRoute}
     * porque responde una pregunta distinta: sin red no hay forma de saber cual es la
     * mas rapida, asi que se elige por la distancia del trazado propio y no se devuelve
     * tiempo estimado. Inventar un tiempo sin consultar seria peor que no darlo.
     *
     * Las rutas sin trazado cargado quedan al final: son candidatas validas, pero no hay
     * con que medirlas para compararlas con las demas.
     *
     * @return La ruta de menor recorrido, sin tiempo estimado.
     * @throws com.fusaroute.domain.exception.NoRouteAvailableException si no hay rutas candidatas.
     */
    RouteSearchResult findBestRouteOffline(String originNeighborhood, Coordinate originCoord,
                       String destNeighborhood, Coordinate destCoord);
}
