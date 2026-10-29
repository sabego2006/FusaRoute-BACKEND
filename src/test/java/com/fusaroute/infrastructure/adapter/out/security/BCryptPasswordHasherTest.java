package com.fusaroute.infrastructure.adapter.out.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hash_with_bcrypt_cost_12() {
        String hash = hasher.hash("Abcdef12");
        // El prefijo $2a$12$ confirma algoritmo BCrypt y factor de trabajo 12.
        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    void hash_is_never_the_plain_text() {
        assertThat(hasher.hash("Abcdef12")).isNotEqualTo("Abcdef12");
    }

    @Test
    void hash_verifies_against_original_password() {
        String hash = hasher.hash("Abcdef12");
        assertThat(new BCryptPasswordEncoder().matches("Abcdef12", hash)).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("otra", hash)).isFalse();
    }

    @Test
    void matches_accepts_correct_password_and_rejects_other() {
        String hash = hasher.hash("Abcdef12");

        assertThat(hasher.matches("Abcdef12", hash)).isTrue();
        assertThat(hasher.matches("otra", hash)).isFalse();
    }

    @Test
    void matches_with_null_hash_returns_false_without_throwing() {
        assertThat(hasher.matches("Abcdef12", null)).isFalse();
    }
}

