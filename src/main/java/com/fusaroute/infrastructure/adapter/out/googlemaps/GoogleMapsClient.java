package com.fusaroute.infrastructure.adapter.out.googlemaps;

import com.fusaroute.domain.model.Coordinate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * Cliente simulado de Google Maps para fines de desarrollo y pruebas.
 * En producción, este componente realizaría llamadas HTTP reales.
 */
@Component
public class GoogleMapsClient {

    private final String apiKey;

    public GoogleMapsClient(@Value("${google.maps.api.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration requestDuration(Coordinate origin, Coordinate destination, List<Coordinate> routePath) {
        // Simulación de latencia de red y respuesta de API
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Genera un tiempo aleatorio basado en la "complejidad" del trazado (cantidad de puntos)
        // para simular variabilidad real.
        int baseMinutes = 5 + new Random().nextInt(15);
        int pathFactor = routePath.size() / 10;

        return Duration.ofMinutes(baseMinutes + pathFactor);
    }
}
