package com.fusaroute.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia Google Maps (RNF-01). Timeouts agresivos a proposito: una
 * llamada lenta a Maps no puede comerse el presupuesto de p95 de 8 s completo,
 * asi que falla rapido y el adaptador la traduce a TravelTimeUnavailableException.
 *
 * {@code @EnableCaching} vive aqui porque sin ella Spring Boot no crea el
 * {@code CacheManager} de Caffeine aunque spring.cache.type=caffeine este
 * declarado en application.properties: la propiedad sola no activa el
 * autoconfig, hace falta esta anotacion en algun {@code @Configuration}.
 */
@Configuration
@EnableCaching
public class MapsConfig {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    @Bean
    public RestClient googleMapsRestClient(@Value("${google.maps.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
