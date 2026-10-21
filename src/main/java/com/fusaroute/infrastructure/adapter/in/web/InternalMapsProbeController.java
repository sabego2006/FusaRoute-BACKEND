package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.infrastructure.adapter.out.maps.GoogleMapsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Probe de RNF-01 (SCRUM-127): valida el instrumento de cache y metricas de
 * {@link GoogleMapsClient} antes de que exista /api/routes/search (RF-04,
 * pendiente). Habla directo con el adaptador, no con un caso de uso, porque es
 * una herramienta de diagnostico y no un flujo de negocio; cuando RF-04 aterrice,
 * el caso de uso real reusa el mismo {@code TravelTimePort} y este controller
 * puede quedarse o retirarse sin tocar el puerto.
 *
 * Apagado salvo que {@code fusaroute.internal.maps-probe-enabled} lo encienda
 * (true por defecto en dev, false en prod). Protegido ademas por JWT en
 * {@code SecurityConfig}: no hay autorizacion por rol porque este repo todavia
 * no mapea roles de usuario a autoridades de Spring Security (ver UserRole).
 */
@RestController
@ConditionalOnProperty(name = "fusaroute.internal.maps-probe-enabled", havingValue = "true")
public class InternalMapsProbeController {

    private final GoogleMapsClient googleMapsClient;

    public InternalMapsProbeController(GoogleMapsClient googleMapsClient) {
        this.googleMapsClient = googleMapsClient;
    }

    @GetMapping("/api/_internal/maps-probe")
    public ResponseEntity<Map<String, Object>> probe(
            @RequestParam double originLat,
            @RequestParam double originLon,
            @RequestParam double destLat,
            @RequestParam double destLon) {

        Coordinate origin = new Coordinate(originLat, originLon);
        Coordinate destination = new Coordinate(destLat, destLon);

        boolean willBeHit = googleMapsClient.isCached(origin, destination);
        Duration travelTime = googleMapsClient.getTravelTime(origin, destination, List.of());

        return ResponseEntity.ok()
                .header("X-Cache", willBeHit ? "HIT" : "MISS")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of(
                        "origin", origin,
                        "destination", destination,
                        "travelTimeSeconds", travelTime.toSeconds()));
    }
}
