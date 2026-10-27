package com.fusaroute.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que las migraciones de Flyway tengan versiones unicas.
 *
 * Existe por un incidente real (SCRUM-179 / SCRUM-166): dos PR mergeados con 25
 * minutos de diferencia eligieron el mismo numero, V7. Git no lo detecta porque son
 * archivos distintos y el merge no da conflicto, pero Flyway rechaza el arranque con
 * "Found more than one migration with version 7" — y como ningun test del build
 * levanta el contexto de Spring, el CI quedo verde con la base rota.
 *
 * Es un test de archivos, no de Spring, a proposito: corre en milisegundos y sin
 * Docker, asi que protege el build en cualquier maquina.
 */
class MigrationVersionsTest {

    private static final Path MIGRATIONS = Paths.get("src/main/resources/db/migration");

    /** Flyway nombra las migraciones versionadas V<version>__<descripcion>.sql */
    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+(?:[._]\\d+)*)__.+\\.sql$");

    @Test
    void no_hay_dos_migraciones_con_la_misma_version() throws IOException {
        Map<String, List<String>> porVersion = migraciones().stream()
                .collect(Collectors.groupingBy(MigrationVersionsTest::version,
                        Collectors.mapping(name -> name, Collectors.toList())));

        List<Map.Entry<String, List<String>>> duplicadas = porVersion.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .toList();

        assertThat(duplicadas)
                .as("Flyway no arranca con versiones repetidas: renumera la que se haya "
                        + "mergeado despues a la siguiente version libre. Duplicadas: %s", duplicadas)
                .isEmpty();
    }

    @Test
    void todas_las_migraciones_siguen_el_formato_de_flyway() throws IOException {
        List<String> invalidas = migraciones().stream()
                .filter(name -> !VERSIONED.matcher(name).matches())
                .toList();

        assertThat(invalidas)
                .as("Nombre esperado: V<version>__<descripcion>.sql")
                .isEmpty();
    }

    private static List<String> migraciones() throws IOException {
        try (Stream<Path> archivos = Files.list(MIGRATIONS)) {
            return archivos
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    private static String version(String fileName) {
        Matcher matcher = VERSIONED.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Migracion con nombre fuera de formato: " + fileName);
        }
        // Flyway trata 1.1 y 1_1 como la misma version, asi que se normaliza antes de comparar.
        return matcher.group(1).replace('_', '.');
    }
}
