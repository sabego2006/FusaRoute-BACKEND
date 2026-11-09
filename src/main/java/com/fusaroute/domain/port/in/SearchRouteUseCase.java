package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.SearchPoint;

/**
 * Caso de uso para buscar la mejor ruta entre un origen y un destino (RF-04, P1).
 *
 * Busqueda por GEOMETRIA: cada punta se resuelve a una coordenada, se proyecta sobre el trazado de cada
 * ruta y la ruta es candidata si el abordaje queda antes que la bajada. Una ruta = un sentido y no hay
 * transbordos.
 */
public interface SearchRouteUseCase {

    /**
     * Mejor ruta con red: se rankea por tiempo propio (recorrido / velocidad de la ruta) y solo la
     * ganadora se cronometra con Google (como maximo una llamada por busqueda); si Google no responde
     * el tiempo queda como estimado.
     *
     * @throws com.fusaroute.domain.exception.InvalidSearchPointException si una punta no se puede resolver
     * @throws com.fusaroute.domain.exception.NoRouteAvailableException si ninguna ruta sirve, ni siquiera
     *     con la caminata ampliada
     */
    RouteSearchResult findBestRoute(SearchPoint origin, SearchPoint destination);

    /**
     * Misma busqueda sin consultar a Google: el tiempo siempre es estimado. Es la que llama la app
     * cuando no tiene red.
     */
    RouteSearchResult findBestRouteOffline(SearchPoint origin, SearchPoint destination);
}
