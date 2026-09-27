package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef12", "Password9", "Xy345678"})
    void acepta_contrasenas_validas(String raw) {
        assertThat(PasswordPolicy.validate(raw)).isEmpty();
    }

    @Test
    void rechaza_menos_de_ocho_caracteres() {
        // 7 caracteres, con mayuscula y numero: solo falla el largo.
        assertThat(PasswordPolicy.validate("Abc123d")).hasSize(1);
    }

    @Test
    void acepta_exactamente_ocho_caracteres() {
        assertThat(PasswordPolicy.validate("Abc1234d")).isEmpty();
    }

    @Test
    void rechaza_sin_mayuscula() {
        assertThat(PasswordPolicy.validate("abcdef12")).hasSize(1);
    }

    @Test
    void rechaza_sin_numero() {
        assertThat(PasswordPolicy.validate("Abcdefgh")).hasSize(1);
    }

    @Test
    void acumula_todos_los_incumplimientos() {
        // Corta, sin mayuscula y sin numero: tres violaciones juntas.
        assertThat(PasswordPolicy.validate("abc")).hasSize(3);
    }

    @Test
    void acepta_72_bytes() {
        // 72 caracteres ASCII = 72 bytes, con mayuscula y numero.
        String raw = "A1" + "a".repeat(70);
        assertThat(PasswordPolicy.validate(raw)).isEmpty();
    }

    @Test
    void rechaza_mas_de_72_bytes_con_tildes() {
        // Cada 'ñ' ocupa 2 bytes en UTF-8: 36 'ñ' + "A1" = 74 bytes aunque sean
        // solo 38 caracteres.
        String raw = "A1" + "ñ".repeat(36);
        List<FieldViolation> violations = PasswordPolicy.validate(raw);
        assertThat(violations).anyMatch(v -> v.message().contains("bytes"));
    }

    @Test
    void rechaza_mas_de_72_bytes_con_emoji() {
        // Un emoji ocupa 4 bytes en UTF-8.
        String raw = "A1" + "😀".repeat(20);
        assertThat(PasswordPolicy.validate(raw)).anyMatch(v -> v.message().contains("bytes"));
    }

    @Test
    void rechaza_nula_o_vacia() {
        assertThat(PasswordPolicy.validate(null)).hasSize(1);
        assertThat(PasswordPolicy.validate("")).hasSize(1);
    }
}
