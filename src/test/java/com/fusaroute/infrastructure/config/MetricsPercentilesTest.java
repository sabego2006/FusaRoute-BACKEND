package com.fusaroute.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que los percentiles declarados en application.properties de verdad se
 * apliquen a los timers que el codigo registra.
 *
 * El nombre de la property tiene que coincidir caracter por caracter con el nombre
 * del meter: Micrometer no hace relaxed binding sobre el nombre del medidor, asi que
 * un guion donde el meter tiene guion bajo deja la property sin efecto y el p95 de
 * RNF-01 simplemente no se publica. Como nada falla en ese caso —el timer sigue
 * existiendo, solo que sin percentiles— es un error que solo se ve mirando
 * /actuator/metrics, y por eso se fija aca.
 */
class MetricsPercentilesTest {

    /** Timers de negocio cuyo p95 sostiene RNF-01. Los nombres salen del codigo que los registra. */
    private static final List<String> TIMERS_CON_P95 = List.of(
            "fusaroute.search.route.latency",
            "fusaroute.maps.travel_time");

    @Test
    void los_timers_de_rnf01_publican_el_percentil_95() {
        for (String timerName : TIMERS_CON_P95) {
            runConPropiedadesReales(registry -> {
                Timer timer = registry.timer(timerName);
                timer.record(Duration.ofMillis(120));

                HistogramSnapshot snapshot = timer.takeSnapshot();
                List<Double> percentiles = Arrays.stream(snapshot.percentileValues())
                        .map(ValueAtPercentile::percentile)
                        .toList();

                assertThat(percentiles)
                        .as("El timer '%s' no publica percentiles. Revisa que "
                                + "management.metrics.distribution.percentiles.%s en "
                                + "application.properties use el nombre exacto del meter",
                                timerName, timerName)
                        .contains(0.95d);
            });
        }
    }

    private void runConPropiedadesReales(java.util.function.Consumer<MeterRegistry> assertions) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MetricsAutoConfiguration.class,
                        CompositeMeterRegistryAutoConfiguration.class,
                        SimpleMetricsExportAutoConfiguration.class))
                .withPropertyValues(propiedadesDeMetricas())
                .run(context -> assertions.accept(context.getBean(MeterRegistry.class)));
    }

    /** Toma del application.properties real solo las claves de metricas, tal cual estan escritas. */
    private static String[] propiedadesDeMetricas() {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get("src/main/resources/application.properties"))) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer application.properties", e);
        }

        List<String> metricas = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("management.metrics.distribution.")) {
                metricas.add(key + "=" + properties.getProperty(key));
            }
        }
        return metricas.toArray(String[]::new);
    }
}
