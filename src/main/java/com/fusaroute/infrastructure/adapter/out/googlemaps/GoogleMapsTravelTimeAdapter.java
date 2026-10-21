package com.fusaroute.infrastructure.adapter.out.googlemaps;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.port.out.TravelTimePort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Adaptador de infraestructura que implementa la obtención de tiempos de viaje mediante Google Maps.
 * Implementa la lógica de caché para optimizar costos y rendimiento.
 */
@Component
public class GoogleMapsTravelTimeAdapter implements TravelTimePort {

    private final GoogleMapsClient googleMapsClient;

    public GoogleMapsTravelTimeAdapter(GoogleMapsClient googleMapsClient) {
        this.googleMapsClient = googleMapsClient;
    }

    @Override
    @Cacheable(value = "travelTimes", key = "{#origin, #destination, #routePath}")
    public Duration getTravelTime(Coordinate origin, Coordinate destination, List<Coordinate> routePath) {
        // En una implementación real, aquí llamaríamos a la API de Google Maps
        // enviando el trazado de la ruta para obtener la duración exacta.
        return googleMapsClient.requestDuration(origin, destination, routePath);
    }
}
