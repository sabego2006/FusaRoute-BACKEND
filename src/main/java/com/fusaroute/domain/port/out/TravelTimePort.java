package com.fusaroute.domain.port.out;

import java.time.Duration;
import java.util.List;
import com.fusaroute.domain.model.Coordinate;

/**
 * Puerto de salida para obtener estimaciones de tiempo de viaje.
 * Desacopla la lógica de negocio de la implementación específica del proveedor de mapas.
 */
public interface TravelTimePort {
    /**
     * Calcula el tiempo estimado de viaje entre un origen y un destino siguiendo el trazado de una ruta específica.
     *
     * @param origin Coordenadas del punto de partida.
     * @param destination Coordenadas del punto de llegada.
     * @param routePath Secuencia de coordenadas que definen la geometría de la ruta.
     * @return La duración estimada del viaje.
     */
    Duration getTravelTime(Coordinate origin, Coordinate destination, List<Coordinate> routePath);
}
