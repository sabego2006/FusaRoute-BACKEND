package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.RouteSearchResult;

import java.util.Optional;

/**
 * Caso de uso para buscar la mejor ruta entre un origen y un destino.
 */
public interface SearchRouteUseCase {
    /**
     * Encuentra la ruta más rápida que conecte el barrio de origen con el de destino.
     *
     * @param originNeighborhood Nombre del barrio de origen.
     * @param originCoord Coordenadas del punto de partida si el cliente las mandó;
     *     vacío si no (SCRUM-130: el frontend ya no pide coordenadas a mano, y la
     *     geolocalización del navegador puede fallar o estar denegada). Vacío no
     *     descarta la búsqueda: se deriva un punto aproximado del nombre del barrio
     *     cuando la ruta tiene trazado cargado.
     * @param destNeighborhood Nombre del barrio de destino.
     * @param destCoord Coordenadas del punto de llegada si el cliente las mandó;
     *     vacío si no — hoy siempre vacío, porque no existe geolocalización del
     *     destino de otra persona.
     * @return La ruta seleccionada como la mejor opción, junto con el tiempo estimado.
     * @throws com.fusaroute.domain.exception.NoRouteAvailableException si no hay rutas candidatas.
     */
    RouteSearchResult findBestRoute(String originNeighborhood, Optional<Coordinate> originCoord,
                       String destNeighborhood, Optional<Coordinate> destCoord);

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
     * @param originCoord vacío en la práctica: el modo offline nunca manda coordenadas.
     * @param destCoord vacío en la práctica, por la misma razón.
     * @return La ruta de menor recorrido, sin tiempo estimado.
     * @throws com.fusaroute.domain.exception.NoRouteAvailableException si no hay rutas candidatas.
     */
    RouteSearchResult findBestRouteOffline(String originNeighborhood, Optional<Coordinate> originCoord,
                       String destNeighborhood, Optional<Coordinate> destCoord);
}
