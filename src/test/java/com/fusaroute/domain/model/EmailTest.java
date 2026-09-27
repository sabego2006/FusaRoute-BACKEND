package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTest {

    @Test
    void normaliza_trim_y_minusculas() {
        assertThat(Email.of("  Ana@X.CO  ").value()).isEqualTo("ana@x.co");
    }

    @Test
    void valida_correo_bien_formado() {
        assertThat(Email.validate("ana@x.co")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "anax.co", "ana@", "@x.co", "ana@x", "ana @x.co", "ana@x .co"})
    void rechaza_correos_invalidos(String raw) {
        assertThat(Email.validate(raw)).isPresent();
    }

    @Test
    void rechaza_correo_que_supera_el_largo_maximo() {
        String local = "a".repeat(180);
        assertThat(Email.validate(local + "@x.co")).isPresent();
    }

    @Test
    void valida_sobre_la_forma_normalizada() {
        // Con mayusculas y espacios alrededor, pero valido tras normalizar.
        assertThat(Email.validate("  ANA@X.CO ")).isEmpty();
    }
}
