package com.fusaroute.infrastructure.adapter.out.maps;

import com.fusaroute.domain.exception.TravelTimeUnavailableException;
import com.fusaroute.domain.model.Coordinate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Prueba el adaptador de la Routes API contra un servidor HTTP simulado (MockRestServiceServer), sin
 * levantar el contexto de Spring: lo que valida RNF-01 es el comportamiento (cache 5 min, error mapeado
 * al dominio, metricas, la peticion que se manda), no el cableado de beans.
 */
class GoogleMapsClientTest {

    private static final String BASE_URL = "https://routes.googleapis.com";
    private static final String URL = BASE_URL + "/directions/v2:computeRoutes";
    private static final Coordinate ORIGIN = new Coordinate(4.3392, -74.3647);
    private static final Coordinate DESTINATION = new Coordinate(4.2889, -74.2167);

    private static final String OK_BODY = """
            {"routes":[{"duration":"925s","distanceMeters":6115}]}
            """;

    private record Harness(GoogleMapsClient client, MockRestServiceServer server, CacheManager cacheManager) {
    }

    private Harness harness(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(GoogleMapsClient.CACHE_NAME);
        GoogleMapsClient client = new GoogleMapsClient(
                builder.build(), cacheManager, new SimpleMeterRegistry(), apiKey);

        return new Harness(client, server, cacheManager);
    }

    private Harness harness() {
        return harness("fake-api-key");
    }

    private static List<Coordinate> pathOf(int points) {
        List<Coordinate> path = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            path.add(new Coordinate(4.30 + i * 0.0001, -74.30));
        }
        return path;
    }

    @Test
    void devuelve_la_duracion_de_la_respuesta_ok() {
        Harness h = harness();
        h.server().expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        Duration result = h.client().getTravelTime(ORIGIN, DESTINATION, List.of());

        assertThat(result).isEqualTo(Duration.ofSeconds(925));
        h.server().verify();
    }

    @Test
    void la_duracion_con_decimales_se_redondea_al_segundo() {
        Harness h = harness();
        h.server().expect(requestTo(URL))
                .andRespond(withSuccess("{\"routes\":[{\"duration\":\"925.6s\"}]}", MediaType.APPLICATION_JSON));

        assertThat(h.client().getTravelTime(ORIGIN, DESTINATION, List.of())).isEqualTo(Duration.ofSeconds(926));
    }

    @Test
    void la_peticion_lleva_la_llave_y_la_field_mask_en_headers_y_pide_trafico_ahora() {
        Harness h = harness();
        h.server().expect(requestTo(URL))
                .andExpect(header("X-Goog-Api-Key", "fake-api-key"))
                .andExpect(header("X-Goog-FieldMask", "routes.duration,routes.distanceMeters"))
                .andExpect(jsonPath("$.routingPreference").value("TRAFFIC_AWARE"))
                .andExpect(jsonPath("$.travelMode").value("DRIVE"))
                .andExpect(jsonPath("$.departureTime").exists())
                .andExpect(jsonPath("$.origin.location.latLng.latitude").value(4.3392))
                .andExpect(jsonPath("$.destination.location.latLng.longitude").value(-74.2167))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        h.client().getTravelTime(ORIGIN, DESTINATION, List.of());

        h.server().verify();
    }

    @Test
    void la_llave_no_viaja_en_la_url() {
        Harness h = harness();
        h.server().expect(requestTo(URL))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        h.client().getTravelTime(ORIGIN, DESTINATION, List.of());

        // requestTo(URL) ya exige la URL exacta sin query: una llave en la URL no pasaria.
        h.server().verify();
    }

    @Test
    void manda_los_puntos_del_tramo_como_waypoints_de_paso_sin_el_primero_ni_el_ultimo() {
        Harness h = harness();
        h.server().expect(requestTo(URL))
                .andExpect(jsonPath("$.intermediates.length()").value(3))
                .andExpect(jsonPath("$.intermediates[0].via").value(true))
                .andExpect(jsonPath("$.intermediates[0].location.latLng.latitude").value(4.3001))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        h.client().getTravelTime(ORIGIN, DESTINATION, pathOf(5));

        h.server().verify();
    }

    @Test
    void sin_puntos_intermedios_no_manda_el_campo() {
        Harness h = harness();
        h.server().expect(requestTo(URL))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("intermediates"))))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        h.client().getTravelTime(ORIGIN, DESTINATION, pathOf(2));

        h.server().verify();
    }

    @Test
    void con_mas_de_25_puntos_intermedios_muestrea_25_incluyendo_los_extremos_del_interior() {
        List<Coordinate> sampled = GoogleMapsClient.sample(pathOf(200));

        assertThat(sampled).hasSize(GoogleMapsClient.MAX_INTERMEDIATES);
        // Interior = path[1..198]; el primero y el ultimo muestreados son los extremos del interior.
        assertThat(sampled.get(0)).isEqualTo(pathOf(200).get(1));
        assertThat(sampled.get(24)).isEqualTo(pathOf(200).get(198));
    }

    @Test
    void la_segunda_llamada_al_mismo_par_no_golpea_la_red_cache_5_min() {
        Harness h = harness();
        // Solo se programa UNA respuesta: si el adaptador llamara dos veces,
        // MockRestServiceServer fallaria por falta de expectativas.
        h.server().expect(requestTo(URL))
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
        h.server().expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> h.client().getTravelTime(ORIGIN, DESTINATION, List.of()))
                .isInstanceOf(TravelTimeUnavailableException.class);
    }

    @Test
    void una_respuesta_sin_rutas_se_traduce_a_excepcion_de_dominio() {
        Harness h = harness();
        h.server().expect(requestTo(URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.client().getTravelTime(ORIGIN, DESTINATION, List.of()))
                .isInstanceOf(TravelTimeUnavailableException.class)
                .hasMessageContaining("ninguna ruta");
    }

    @Test
    void una_duracion_ilegible_se_traduce_a_excepcion_de_dominio() {
        Harness h = harness();
        h.server().expect(requestTo(URL))
                .andRespond(withSuccess("{\"routes\":[{\"duration\":\"mucho\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.client().getTravelTime(ORIGIN, DESTINATION, List.of()))
                .isInstanceOf(TravelTimeUnavailableException.class)
                .hasMessageContaining("ilegible");
    }

    @Test
    void un_403_por_llave_invalida_es_excepcion_de_dominio_y_el_mensaje_no_filtra_la_llave() {
        Harness h = harness();
        h.server().expect(requestTo(URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"API key fake-api-key not valid\"}}"));

        assertThatThrownBy(() -> h.client().getTravelTime(ORIGIN, DESTINATION, List.of()))
                .isInstanceOf(TravelTimeUnavailableException.class)
                .hasMessageContaining("Routes API")
                .hasMessageNotContaining("fake-api-key");
    }

    @Test
    void sin_llave_no_llama_a_nadie_y_lo_dice_para_que_el_caso_de_uso_use_el_estimado() {
        Harness h = harness("");
        // Ninguna expectativa programada: cualquier llamada de red haria fallar al servidor simulado.

        assertThatThrownBy(() -> h.client().getTravelTime(ORIGIN, DESTINATION, List.of()))
                .isInstanceOf(TravelTimeUnavailableException.class)
                .hasMessageContaining("GOOGLE_MAPS_API_KEY");
        h.server().verify();
    }

    @Test
    void un_fallo_no_contamina_el_cache() {
        Harness h = harness();
        h.server().expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> h.client().getTravelTime(ORIGIN, DESTINATION, List.of()))
                .isInstanceOf(TravelTimeUnavailableException.class);

        assertThat(h.client().isCached(ORIGIN, DESTINATION)).isFalse();
    }
}
