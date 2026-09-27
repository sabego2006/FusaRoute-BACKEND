package com.fusaroute.infrastructure.adapter.out.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
}
