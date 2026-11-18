package com.fusaroute.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia Google Maps (RNF-01). Timeouts agresivos a proposito: una
 * llamada lenta a Maps no puede comerse el presupuesto de p95 de 8 s completo,
 * asi que falla rapido y el adaptador la traduce a TravelTimeUnavailableException,
 * que el caso de uso degrada a {@code etaSource: ESTIMATED}. La busqueda siempre
 * devuelve una ruta y un numero de minutos; lo unico que se pierde es el "con el
 * trafico de ahora".
 *
 * <p><b>De donde salen los numeros (T15, SCRUM-199).</b> Se midieron cinco busquedas
 * en linea y cuatro sin conexion contra PROD, sobre el mismo par de la demo y pares
 * nuevos:
 *
 * <ul>
 *   <li>{@code /search/offline} (nunca llama a Google): 0,49 a 1,06 s.</li>
 *   <li>{@code /search} en estado estable: 0,60 a 0,76 s, es decir Google aporta
 *       menos de 0,2 s.</li>
 *   <li>{@code /search} con la conexion a Google en frio: 2,89 s, unos 2,3 s de mas.</li>
 * </ul>
 *
 * El peor caso observado de la llamada a Google es entonces ~2,3 s. Con 1,5 s de
 * conexion y 3 s de lectura, ninguna llamada que iba a terminar se corta, y el peor
 * caso baja de 7 s (2 + 5) a 4,5 s. Bajarlo mas cortaria la primera llamada del dia,
 * que es justo la del comite.
 *
 * <p>Los dos valores son propiedades y no constantes para poder apretarlos en PROD sin
 * recompilar si la medicion del dia de la sustentacion dice otra cosa.
 *
 * {@code @EnableCaching} vive aqui porque sin ella Spring Boot no crea el
 * {@code CacheManager} de Caffeine aunque spring.cache.type=caffeine este
 * declarado en application.properties: la propiedad sola no activa el
 * autoconfig, hace falta esta anotacion en algun {@code @Configuration}.
 */
@Configuration
@EnableCaching
public class MapsConfig {

    @Bean
    public RestClient googleMapsRestClient(
            @Value("${google.routes.base-url:https://routes.googleapis.com}") String baseUrl,
            @Value("${google.routes.connect-timeout-ms:1500}") int connectTimeoutMs,
            @Value("${google.routes.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
