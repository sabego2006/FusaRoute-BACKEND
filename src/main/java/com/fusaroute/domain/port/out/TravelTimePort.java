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
     * Calcula el tiempo estimado del tramo en buseta entre dos puntos de una ruta: el de
     * abordaje y el de bajada.
     *
     * El proveedor toma su propio camino entre esos dos puntos y no sigue necesariamente
     * el {@code routePath}; por eso el trazado se pasa como contexto, para que el
     * adaptador lo use si puede (p. ej. como clave de caché o para una futura API que sí
     * siga la ruta), no como garantía de que el tiempo recorra esa geometría.
     *
     * @param origin Punto de abordaje (proyección del origen sobre el trazado, o el
     *     origen mismo si la ruta no tiene trazado cargado).
     * @param destination Punto de bajada, de forma análoga.
     * @param routePath Secuencia de coordenadas que definen la geometría de la ruta;
     *     informativo para el adaptador.
     * @return La duración estimada del tramo en buseta.
     */
    Duration getTravelTime(Coordinate origin, Coordinate destination, List<Coordinate> routePath);
}
