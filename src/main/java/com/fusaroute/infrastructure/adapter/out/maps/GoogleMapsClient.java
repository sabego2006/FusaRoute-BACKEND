package com.fusaroute.infrastructure.adapter.out.maps;

import com.fusaroute.domain.exception.TravelTimeUnavailableException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.port.out.TravelTimePort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adaptador saliente hacia la Routes API de Google ({@code computeRoutes}, P1), que reemplaza a la
 * Distance Matrix legada.
 *
 * Google solo CRONOMETRA el tramo en buseta de la ruta ya elegida por el dominio; nunca elige (RNF-03).
 * Para que el tiempo siga el recorrido de la buseta y no el camino mas corto de un carro, se mandan
 * hasta {@value #MAX_INTERMEDIATES} puntos del trazado entre abordaje y bajada como waypoints de paso
 * ({@code via}), con {@code TRAFFIC_AWARE} y salida "ahora". La field mask pide solo
 * {@code duration} y {@code distanceMeters}, que es lo unico que se factura como basico.
 *
 * El cache es manual, no {@code @Cacheable}: el proxy de Spring Cache no deja saber desde dentro del
 * metodo si hubo hit o miss, y RNF-01 exige la metrica {@code fusaroute.maps.cache}. La clave es el par
 * origen-destino (5 min, TTL en application.properties): una busqueda repetida no vuelve a gastar
 * cuota. Sin llave configurada no se llama a nadie y se lanza {@link TravelTimeUnavailableException},
 * que el caso de uso traduce a tiempo estimado.
 */
@Component
public class GoogleMapsClient implements TravelTimePort {

    static final String CACHE_NAME = "googleMapsTravelTime";
    static final int MAX_INTERMEDIATES = 25;
    private static final String COMPUTE_ROUTES_PATH = "/directions/v2:computeRoutes";
    private static final String FIELD_MASK = "routes.duration,routes.distanceMeters";
    private static final Pattern SECONDS = Pattern.compile("^(\\d+(?:\\.\\d+)?)s$");

    private final RestClient restClient;
    private final CacheManager cacheManager;
    private final String apiKey;
    private final Timer okTimer;
    private final Timer errorTimer;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public GoogleMapsClient(RestClient googleMapsRestClient,
                            CacheManager cacheManager,
                            MeterRegistry meterRegistry,
                            @Value("${google.maps.api.key:}") String apiKey) {
        this.restClient = googleMapsRestClient;
        this.cacheManager = cacheManager;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.okTimer = Timer.builder("fusaroute.maps.travel_time")
                .tag("status", "ok")
                .description("Latencia de la llamada real (sin cache) a la Routes API")
                .register(meterRegistry);
        this.errorTimer = Timer.builder("fusaroute.maps.travel_time")
                .tag("status", "error")
                .register(meterRegistry);
        this.cacheHitCounter = Counter.builder("fusaroute.maps.cache")
                .tag("result", "hit")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("fusaroute.maps.cache")
                .tag("result", "miss")
                .register(meterRegistry);
    }

    @Override
    public Duration getTravelTime(Coordinate origin, Coordinate destination, List<Coordinate> routePath) {
        String key = cacheKey(origin, destination);
        Cache cache = cache();

        Cache.ValueWrapper cached = cache.get(key);
        if (cached != null) {
            cacheHitCounter.increment();
            return (Duration) cached.get();
        }

        cacheMissCounter.increment();
        Duration result = fetchFromGoogle(origin, destination, routePath);
        cache.put(key, result);
        return result;
    }

    /**
     * Permite al probe de RNF-01 (SCRUM-127) reportar si la siguiente llamada sera un hit, sin
     * duplicar logica de cache fuera de este adaptador.
     */
    public boolean isCached(Coordinate origin, Coordinate destination) {
        return cache().get(cacheKey(origin, destination)) != null;
    }

    private Duration fetchFromGoogle(Coordinate origin, Coordinate destination, List<Coordinate> routePath) {
        if (apiKey.isEmpty()) {
            throw new TravelTimeUnavailableException("GOOGLE_MAPS_API_KEY no esta configurada", null);
        }
        Timer.Sample sample = Timer.start();
        try {
            RoutesApiResponse response = restClient.post()
                    .uri(COMPUTE_ROUTES_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(requestBody(origin, destination, routePath))
                    .retrieve()
                    .body(RoutesApiResponse.class);

            // extractDuration tambien puede fallar (respuesta sin ruta); el timer solo cuenta como
            // "ok" si de verdad hay una duracion que devolver.
            Duration duration = extractDuration(response);
            sample.stop(okTimer);
            return duration;
        } catch (RestClientException e) {
            sample.stop(errorTimer);
            throw new TravelTimeUnavailableException(
                    "Google Maps no respondio la Routes API" + sanitize(e.getMessage()), e);
        } catch (TravelTimeUnavailableException e) {
            sample.stop(errorTimer);
            throw e;
        }
    }

    private static Map<String, Object> requestBody(Coordinate origin, Coordinate destination,
                                                   List<Coordinate> routePath) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", waypoint(origin, false));
        body.put("destination", waypoint(destination, false));
        List<Map<String, Object>> intermediates = new ArrayList<>();
        for (Coordinate point : sample(routePath)) {
            intermediates.add(waypoint(point, true));
        }
        if (!intermediates.isEmpty()) {
            body.put("intermediates", intermediates);
        }
        body.put("travelMode", "DRIVE");
        body.put("routingPreference", "TRAFFIC_AWARE");
        // Google rechaza una salida en el pasado: "ahora" llegaria vencida al servidor, asi que se
        // adelanta unos segundos.
        body.put("departureTime", Instant.now().plusSeconds(30).toString());
        return body;
    }

    private static Map<String, Object> waypoint(Coordinate point, boolean via) {
        Map<String, Object> latLng = new LinkedHashMap<>();
        latLng.put("latitude", point.latitude());
        latLng.put("longitude", point.longitude());
        Map<String, Object> waypoint = new LinkedHashMap<>();
        waypoint.put("location", Map.of("latLng", latLng));
        if (via) {
            // Punto de paso, no parada: sin esto Google trata cada intermedio como una escala.
            waypoint.put("via", true);
        }
        return waypoint;
    }

    /**
     * Hasta {@value #MAX_INTERMEDIATES} puntos del tramo, repartidos parejo, sin el primero ni el
     * ultimo (esos ya son origen y destino).
     */
    static List<Coordinate> sample(List<Coordinate> routePath) {
        if (routePath == null || routePath.size() <= 2) {
            return List.of();
        }
        List<Coordinate> interior = routePath.subList(1, routePath.size() - 1);
        if (interior.size() <= MAX_INTERMEDIATES) {
            return List.copyOf(interior);
        }
        List<Coordinate> sampled = new ArrayList<>(MAX_INTERMEDIATES);
        for (int i = 0; i < MAX_INTERMEDIATES; i++) {
            sampled.add(interior.get((int) Math.round(i * (interior.size() - 1d) / (MAX_INTERMEDIATES - 1))));
        }
        return sampled;
    }

    private Duration extractDuration(RoutesApiResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            throw new TravelTimeUnavailableException("La Routes API no devolvio ninguna ruta", null);
        }
        String raw = response.routes().get(0).duration();
        Matcher matcher = raw == null ? null : SECONDS.matcher(raw);
        if (matcher == null || !matcher.matches()) {
            throw new TravelTimeUnavailableException("La Routes API devolvio una duracion ilegible: " + raw, null);
        }
        return Duration.ofSeconds(Math.round(Double.parseDouble(matcher.group(1))));
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException(
                    "El cache '" + CACHE_NAME + "' no esta configurado (spring.cache.cache-names)");
        }
        return cache;
    }

    private String cacheKey(Coordinate origin, Coordinate destination) {
        return toLatLng(origin) + ">" + toLatLng(destination);
    }

    private String toLatLng(Coordinate coordinate) {
        return String.format(Locale.ROOT, "%.6f,%.6f", coordinate.latitude(), coordinate.longitude());
    }

    /**
     * Devuelve ": <mensaje>" o cadena vacia, recortado y sin la llave si por alguna razon aparece: ni el
     * log ni la ProblemDetail deben depender de que Google nunca la repita.
     */
    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String safe = apiKey.isEmpty() ? message : message.replace(apiKey, "***");
        return ": " + (safe.length() > 240 ? safe.substring(0, 240) + "..." : safe);
    }
}
