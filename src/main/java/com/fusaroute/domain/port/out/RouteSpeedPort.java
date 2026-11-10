package com.fusaroute.domain.port.out;

import java.util.List;
import java.util.Map;

/**
 * Velocidades observadas por ruta (V22): lo que Google (con trafico) midio en busquedas anteriores,
 * para calibrar el tiempo propio con el que se rankea.
 */
public interface RouteSpeedPort {

    /**
     * Las ultimas {@code perRoute} velocidades (km/h) de cada ruta que tenga alguna, de la mas reciente
     * a la mas vieja. La mediana y el minimo de muestras los decide el dominio.
     */
    Map<Long, List<Double>> recentObservations(int perRoute);

    /** Guarda la velocidad observada en una busqueda para esta ruta. */
    void record(long routeId, double observedKmh);
}
