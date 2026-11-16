package com.fusaroute.infrastructure.adapter.out.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashea_con_bcrypt_costo_12() {
        String hash = hasher.hash("Abcdef12");
        // El prefijo $2a$12$ confirma algoritmo BCrypt y factor de trabajo 12.
        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    void el_hash_nunca_es_el_texto_plano() {
        assertThat(hasher.hash("Abcdef12")).isNotEqualTo("Abcdef12");
    }

    @Test
    void el_hash_verifica_contra_la_contrasena_original() {
        String hash = hasher.hash("Abcdef12");
        assertThat(new BCryptPasswordEncoder().matches("Abcdef12", hash)).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("otra", hash)).isFalse();
    }

    @Test
    void matches_acepta_la_contrasena_correcta_y_rechaza_otra() {
        String hash = hasher.hash("Abcdef12");

        assertThat(hasher.matches("Abcdef12", hash)).isTrue();
        assertThat(hasher.matches("otra", hash)).isFalse();
    }

    @Test
    void matches_con_hash_nulo_devuelve_false_sin_lanzar() {
        assertThat(hasher.matches("Abcdef12", null)).isFalse();
    }

    /**
     * Sonar S6437 (T03): the filler hash must not come from a string literal. A literal
     * passed to encode() reads as a hard-coded credential, so this test scans the source
     * and fails the moment one comes back. The value itself never mattered: only the
     * BCrypt cost of comparing against it does.
     */
    @Test
    void el_hash_de_relleno_no_sale_de_un_literal() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fusaroute/infrastructure/adapter/out/security/BCryptPasswordHasher.java"));

        assertThat(source).doesNotContainPattern("encode\\(\\s*\"");
        assertThat(source).contains("SecureRandom");
    }

    /**
     * Property the filler exists for: an unknown email costs the same BCrypt work as a
     * wrong password on a real account, so timing does not reveal which emails exist.
     * Bounds are loose on purpose (the point is "not instant", not a benchmark).
     */
    @Test
    void matches_con_hash_nulo_tarda_lo_mismo_que_con_una_cuenta_real() {
        String hash = hasher.hash("Abcdef12");
        hasher.matches("calentar", hash);

        long real = medianNanos(() -> hasher.matches("otra", hash));
        long filler = medianNanos(() -> hasher.matches("otra", null));

        assertThat(filler).isGreaterThan(real / 4);
    }

    private static long medianNanos(Runnable action) {
        long[] samples = new long[3];
        for (int i = 0; i < samples.length; i++) {
            long start = System.nanoTime();
            action.run();
            samples[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(samples);
        return samples[1];
    }
}
