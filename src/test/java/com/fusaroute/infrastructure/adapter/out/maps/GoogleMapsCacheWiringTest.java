package com.fusaroute.infrastructure.adapter.out.maps;

import com.fusaroute.infrastructure.config.MapsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el cache que GoogleMapsClient pide por nombre sea el mismo que la
 * configuracion de verdad construye.
 *
 * Existe por un bug real (SCRUM-49 / SCRUM-127): un {@code @Bean CacheManager}
 * manual declaraba el cache "travelTimes" mientras el adaptador pedia
 * "googleMapsTravelTime" desde application.properties. Al haber un bean explicito,
 * el autoconfig de Spring Boot ({@code @ConditionalOnMissingBean}) no se disparaba y
 * las properties quedaban ignoradas, asi que cada busqueda real reventaba con
 * IllegalStateException. GoogleMapsClientTest no lo veia porque construye el
 * CacheManager a mano con el nombre correcto, salteandose a Spring.
 */
class GoogleMapsCacheWiringTest {

    private static final Path CONFIG_PACKAGE = Paths.get("target/classes/com/fusaroute/infrastructure/config");

    @Test
    void el_autoconfig_crea_el_cache_que_pide_el_adaptador() {
        Properties properties = applicationProperties();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
                // MapsConfig es quien aporta el @EnableCaching sin el que no hay CacheManager.
                .withUserConfiguration(MapsConfig.class)
                .withPropertyValues(
                        "google.maps.base-url=https://maps.googleapis.com/maps/api",
                        "spring.cache.type=" + properties.getProperty("spring.cache.type"),
                        "spring.cache.cache-names=" + properties.getProperty("spring.cache.cache-names"),
                        "spring.cache.caffeine.spec=" + properties.getProperty("spring.cache.caffeine.spec"))
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class).getCache(GoogleMapsClient.CACHE_NAME))
                            .as("application.properties debe declarar el cache '%s', que es el que "
                                    + "GoogleMapsClient pide por nombre", GoogleMapsClient.CACHE_NAME)
                            .isNotNull();
                });
    }

    @Test
    void el_ttl_del_cache_sigue_siendo_de_cinco_minutos() {
        // RNF-01: el cache de 5 min por par origen-destino es parte del diseno de la
        // metrica de p95, no una optimizacion opcional. Si alguien lo cambia, que se vea.
        assertThat(applicationProperties().getProperty("spring.cache.caffeine.spec"))
                .contains("expireAfterWrite=5m");
    }

    @Test
    void ninguna_clase_de_config_declara_un_cachemanager_a_mano() throws Exception {
        List<String> infractoras = new ArrayList<>();

        for (Class<?> config : clasesDeConfig()) {
            for (Method method : config.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Bean.class)
                        && CacheManager.class.isAssignableFrom(method.getReturnType())) {
                    infractoras.add(config.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(infractoras)
                .as("El CacheManager lo construye el autoconfig desde application.properties. "
                        + "Un @Bean manual lo anula por @ConditionalOnMissingBean y deja las "
                        + "properties (nombre del cache y TTL) sin efecto")
                .isEmpty();
    }

    private static List<Class<?>> clasesDeConfig() throws Exception {
        List<Class<?>> clases = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(CONFIG_PACKAGE)) {
            for (Path archivo : archivos.toList()) {
                String nombre = archivo.getFileName().toString();
                if (!nombre.endsWith(".class") || nombre.contains("$")) {
                    continue;
                }
                clases.add(Class.forName("com.fusaroute.infrastructure.config."
                        + nombre.substring(0, nombre.length() - ".class".length())));
            }
        }
        assertThat(clases).as("no se encontraron clases compiladas en %s", CONFIG_PACKAGE).isNotEmpty();
        return clases;
    }

    private static Properties applicationProperties() {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get("src/main/resources/application.properties"))) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer application.properties", e);
        }
        return properties;
    }
}
