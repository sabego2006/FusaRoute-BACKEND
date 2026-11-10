package com.fusaroute.infrastructure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V21 y V22 (SCRUM-188) llegaron a main despues de V24 y V25. Una base que ya aplico V24/V25 debe poder aplicar las
 * pendientes de version menor: sin {@code outOfOrder} el arranque falla con "Detected resolved migration not applied".
 * Se reproduce migrando primero SIN V21/V22 (la base "ya desplegada") y despues con todas.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayOutOfOrderIT {

    private static final Path MIGRATIONS = Paths.get("src/main/resources/db/migration");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static Flyway flyway(String location, boolean outOfOrder) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("public")
                .locations("filesystem:" + location)
                .outOfOrder(outOfOrder)
                .load();
    }

    @Test
    void una_base_que_ya_aplico_v24_y_v25_acepta_v21_y_v22_solo_con_out_of_order() throws IOException {
        Path sinV21yV22 = Files.createTempDirectory("migraciones-desplegadas");
        try (Stream<Path> todas = Files.list(MIGRATIONS)) {
            for (Path m : todas.toList()) {
                String nombre = m.getFileName().toString();
                if (!nombre.startsWith("V21__") && !nombre.startsWith("V22__")) {
                    Files.copy(m, sinV21yV22.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        flyway(sinV21yV22.toString(), false).migrate();

        // Sin out-of-order, la base ya desplegada rechaza las migraciones que quedaron "atras".
        assertThatThrownBy(() -> flyway(MIGRATIONS.toAbsolutePath().toString(), false).migrate())
                .isInstanceOf(FlywayValidateException.class);

        // Con out-of-order las aplica y deja el esquema completo.
        int aplicadas = flyway(MIGRATIONS.toAbsolutePath().toString(), true).migrate().migrationsExecuted;
        assertThat(aplicadas).isEqualTo(2);
    }
}
