package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void nulo_o_vacio_es_valido_porque_el_campo_es_opcional(String phone) {
        assertThat(PhoneNumber.validate(phone)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "3101234567", "573101234567890"})
    void telefono_con_7_a_15_digitos_es_valido(String phone) {
        assertThat(PhoneNumber.validate(phone)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "1234567890123456", "abc", "+57310", "310-123"})
    void telefono_invalido_devuelve_violacion(String phone) {
        Optional<FieldViolation> result = PhoneNumber.validate(phone);
        assertThat(result).isPresent();
        assertThat(result.get().field()).isEqualTo("phone");
    }

    @Test
    void normalize_con_espacios_los_recorta() {
        assertThat(PhoneNumber.normalize("  3101234567  ")).isEqualTo("3101234567");
    }

    @Test
    void normalize_nulo_devuelve_nulo() {
        assertThat(PhoneNumber.normalize(null)).isNull();
    }
}
