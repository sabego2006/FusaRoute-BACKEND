package com.fusaroute.infrastructure.adapter.out.maps;

import com.fusaroute.domain.exception.TravelTimeUnavailableException;
import com.fusaroute.domain.model.Coordinate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Prueba el adaptador contra un servidor HTTP simulado (MockRestServiceServer),
 * sin levantar el contexto de Spring: lo que valida RNF-01 es el comportamiento
 * (cache 5 min, error mapeado al dominio, metricas), no el cableado de beans.
 */
class GoogleMapsClientTest {

    private static final String BASE_URL = "https://maps.googleapis.com/maps/api";
    private static final Coordinate ORIGIN = new Coordinate(4.3392, -74.3647);
    private static final Coordinate DESTINATION = new Coordinate(4.2889, -74.2167);

    private static final String OK_BODY = """
            {"status":"OK","rows":[{"elements":[{"status":"OK","duration":{"value":925}}]}]}
            """;

    private static final String ELEMENT_NOT_FOUND_BODY = """
            {"status":"OK","rows":[{"elements":[{"status":"NOT_FOUND"}]}]}
            """;

    private record Harness(GoogleMapsClient client, MockRestServiceServer server, CacheManager cacheManager) {
    }

    private Harness harness() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(GoogleMapsClient.CACHE_NAME);
        GoogleMapsClient client = new GoogleMapsClient(
                builder.build(), cacheManager, new SimpleMeterRegistry(), "fake-api-key");

        return new Harness(client, server, cacheManager);
    }

    @Test
    void devuelve_la_duracion_de_la_respuesta_ok() {
        Harness h = harness();
        h.server().expect(requestTo(org.hamcrest.Matchers.containsString("/distancematrix/json")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        Duration result = h.client().getTravelTime(ORIGIN, DESTINATION, List.of());

        assertThat(result).isEqualTo(Duration.ofSeconds(925));
    }

    @Test
    void la_segunda_llamada_al_mismo_par_no_golpea_la_red_cache_5_min() {
        Harness h = harness();
        // Solo se programa UNA respuesta: si el adaptador llamara dos veces,
        // MockRestServiceServer fallaria por falta de expectativas.
        h.server().expect(requestTo(org.hamcrest.Matchers.containsString("/distancematrix/json")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(h.client().isCached(ORIGIN, DESTINATION)).isFalse();

        Duration first = h.client().getTravelTime(ORIGIN, DESTINATION, List.of());

        assertThat(h.client().isCached(ORIGIN, DESTINATION)).isTrue();

        Duration second = h.client().getTravelTime(ORIGIN, DESTINATION, List.of());

        assertThat(second).isEqualTo(first);
        h.server().verify();
    }

    @Test
    void un_5xx_de_google_se_traduce_a_excepcion_de_dominio() {
        Harness h = harness();
        h.server().expect(requestTo(org.hamcrest.Matchers.containsString("/distancematrix/json")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> h.client().getTravelTime(ORIGIN, DESTINATION, List.of()))
                .isInstanceOf(TravelTimeUnavailableException.class);
    }

    @Test
    void un_elemento_sin_status_ok_se_traduce_a_excepcion_de_dominio() {
        Harness h = harness();
        h.server().expect(requestTo(org.hamcrest.Matchers.containsString("/distancematrix/json")))
                .andRespond(withSuccess(ELEMENT_NOT_FOUND_BODY, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.client().getTravelTime(ORIGIN, DESTINATION, List.of()))
                .isInstanceOf(TravelTimeUnavailableException.class)
                .hasMessageContaining("NOT_FOUND");
    }

    @Test
    void un_fallo_no_contamina_el_cache() {
        Harness h = harness();
        h.server().expect(requestTo(org.hamcrest.Matchers.containsString("/distancematrix/json")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> h.client().getTravelTime(ORIGIN, DESTINATION, List.of()))
                .isInstanceOf(TravelTimeUnavailableException.class);

        assertThat(h.client().isCached(ORIGIN, DESTINATION)).isFalse();
    }
}
