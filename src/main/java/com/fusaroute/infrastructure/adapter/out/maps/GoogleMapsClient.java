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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador saliente hacia la Distance Matrix API de Google (RNF-01).
 *
 * El cache es manual, no {@code @Cacheable}: el proxy de Spring Cache no deja
 * saber desde dentro del metodo si hubo hit o miss, y RNF-01 exige poder
 * instrumentar esa metrica ({@code fusaroute.maps.cache}), no solo la latencia.
 *
 * {@code routePath} llega por el puerto pero no se usa en la llamada real: la
 * Distance Matrix API cotiza por par origen-destino, no por trazado (seguir un
 * trazado especifico exigiria la Roads API, fuera de alcance de RNF-01). La
 * clave del cache tampoco lo incluye, a proposito: es exactamente el "cache de
 * 5 min por par origen-destino" que pide el RNF, con el TTL fijado en
 * application.properties (spring.cache.caffeine.spec).
 */
@Component
public class GoogleMapsClient implements TravelTimePort {

    static final String CACHE_NAME = "googleMapsTravelTime";

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
                            @Value("${google.maps.api.key}") String apiKey) {
        this.restClient = googleMapsRestClient;
        this.cacheManager = cacheManager;
        this.apiKey = apiKey;
        this.okTimer = Timer.builder("fusaroute.maps.travel_time")
                .tag("status", "ok")
                .description("Latencia de la llamada real (sin cache) a la Distance Matrix API")
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
        Duration result = fetchFromGoogle(origin, destination);
        cache.put(key, result);
        return result;
    }

    /**
     * Permite al probe de RNF-01 (SCRUM-127) reportar si la siguiente llamada
     * sera un hit, sin duplicar logica de cache fuera de este adaptador.
     */
    public boolean isCached(Coordinate origin, Coordinate destination) {
        return cache().get(cacheKey(origin, destination)) != null;
    }

    private Duration fetchFromGoogle(Coordinate origin, Coordinate destination) {
        Timer.Sample sample = Timer.start();
        try {
            DistanceMatrixResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/distancematrix/json")
                            .queryParam("origins", toLatLng(origin))
                            .queryParam("destinations", toLatLng(destination))
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(DistanceMatrixResponse.class);

            // extractDuration tambien puede fallar (status de negocio distinto de OK);
            // el timer solo cuenta como "ok" si de verdad hay una duracion que devolver.
            Duration duration = extractDuration(response);
            sample.stop(okTimer);
            return duration;
        } catch (RestClientException e) {
            sample.stop(errorTimer);
            throw new TravelTimeUnavailableException(
                    "Google Maps no respondio la Distance Matrix API", e);
        } catch (TravelTimeUnavailableException e) {
            sample.stop(errorTimer);
            throw e;
        }
    }

    private Duration extractDuration(DistanceMatrixResponse response) {
        if (response == null) {
            throw new TravelTimeUnavailableException("Respuesta de Distance Matrix vacia", null);
        }
        if (!"OK".equals(response.status())) {
            throw new TravelTimeUnavailableException(
                    "Distance Matrix respondio status=" + response.status(), null);
        }
        if (response.rows().isEmpty()) {
            throw new TravelTimeUnavailableException(
                    "Distance Matrix respondio status=OK pero sin filas", null);
        }
        DistanceMatrixResponse.Element element = response.rows().get(0).elements().get(0);
        if (!"OK".equals(element.status())) {
            throw new TravelTimeUnavailableException(
                    "Distance Matrix no pudo calcular el par origen-destino: " + element.status(), null);
        }
        return Duration.ofSeconds(element.duration().value());
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
}
