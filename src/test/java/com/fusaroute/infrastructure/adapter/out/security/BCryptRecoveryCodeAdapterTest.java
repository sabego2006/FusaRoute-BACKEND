package com.fusaroute.infrastructure.adapter.out.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el adaptador real de respaldos. No mockea BCrypt — queremos verificar
 * que el hash guardado efectivamente verifica contra el plain correspondiente.
 */
class BCryptRecoveryCodeAdapterTest {

    private final BCryptRecoveryCodeAdapter adapter = new BCryptRecoveryCodeAdapter();

    @Test
    void genera_codigos_del_largo_y_alfabeto_esperados() {
        List<String> codes = adapter.generatePlainCodes(10);

        assertThat(codes).hasSize(10);
        assertThat(codes).allSatisfy(code -> {
            assertThat(code).hasSize(10);
            // Sin 0/O ni 1/l: alfabeto {A-Z sin O,I,L} u {2-9}.
            assertThat(code).matches("[A-HJ-NP-Z2-9]+");
        });
        // Dos invocaciones producen conjuntos distintos (SecureRandom).
        List<String> otro = adapter.generatePlainCodes(10);
        assertThat(codes).isNotEqualTo(otro);
    }

    @Test
    void hash_y_matches_verifican_el_mismo_codigo() {
        String plain = adapter.generatePlainCodes(1).get(0);

        String hash = adapter.hash(plain);

        assertThat(adapter.matches(plain, hash)).isTrue();
        assertThat(adapter.matches("OTRO123456", hash)).isFalse();
    }

    @Test
    void matches_con_hash_o_plain_nulos_devuelve_false_sin_lanzar() {
        assertThat(adapter.matches(null, "$2a$12$hash")).isFalse();
        assertThat(adapter.matches("AAAA1111BB", null)).isFalse();
        assertThat(adapter.matches(null, null)).isFalse();
    }
}
