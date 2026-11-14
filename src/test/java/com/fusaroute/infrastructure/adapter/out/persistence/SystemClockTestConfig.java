package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;

/**
 * El {@code Clock} de produccion vive en {@code UseCaseConfig}, que un {@code @DataJpaTest} no carga. Los
 * adaptadores de rutas y de viajes lo necesitan para decidir si una ruta esta suspendida (RF-12): en estos
 * tests basta el reloj del sistema, porque ninguna siembra una suspension.
 */
@TestConfiguration
class SystemClockTestConfig {

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
