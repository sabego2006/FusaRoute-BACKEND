package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validaciones de {@link SearchRecord} (RF-07, SCRUM-20).
 *
 * Es un record de dominio puro, asi que el test no levanta Spring ni usa Mockito:
 * {@code TestArchitectureTest} lo prohibe para {@code domain..} y {@code application..}.
 */
class SearchRecordTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T09:15:00Z");

    @Test
    void el_tope_por_usuario_es_seis() {
        // El numero lo fija el criterio de aceptacion de la historia, no una
        // conveniencia de implementacion: si alguien lo cambia, este test lo delata.
        assertThat(SearchRecord.MAX_PER_USER).isEqualTo(6);
    }

    @Test
    void una_entrada_nueva_nace_sin_id() {
        SearchRecord record = SearchRecord.of(7L, "Maiz Amarillo", "San Antonio", AHORA);

        assertThat(record.id()).isNull();
        assertThat(record.userId()).isEqualTo(7L);
        assertThat(record.origin()).isEqualTo("Maiz Amarillo");
        assertThat(record.destination()).isEqualTo("San Antonio");
        assertThat(record.searchedAt()).isEqualTo(AHORA);
    }

    @Test
    void el_historial_siempre_pertenece_a_un_usuario() {
        assertThatThrownBy(() -> SearchRecord.of(null, "Centro", "Terminal", AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pertenece a un usuario");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void el_origen_no_puede_ir_vacio(String origenInvalido) {
        assertThatThrownBy(() -> SearchRecord.of(7L, origenInvalido, "Terminal", AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origen");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void el_destino_no_puede_ir_vacio(String destinoInvalido) {
        assertThatThrownBy(() -> SearchRecord.of(7L, "Centro", destinoInvalido, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destino");
    }

    @Test
    void la_busqueda_necesita_una_fecha() {
        assertThatThrownBy(() -> SearchRecord.of(7L, "Centro", "Terminal", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecha");
    }

    @Test
    void los_barrios_se_guardan_sin_espacios_sobrantes() {
        // El barrio llega del parametro de la peticion tal como lo escribio el
        // usuario. Sin el trim, "  Centro " y "Centro" se verian como dos entradas
        // distintas del historial siendo la misma busqueda.
        SearchRecord record = SearchRecord.of(7L, "  Maiz Amarillo ", " San Antonio  ", AHORA);

        assertThat(record.origin()).isEqualTo("Maiz Amarillo");
        assertThat(record.destination()).isEqualTo("San Antonio");
    }

    @Test
    void una_entrada_ya_persistida_conserva_su_id() {
        SearchRecord record = new SearchRecord(42L, 7L, "Centro", "Terminal", AHORA);

        assertThat(record.id()).isEqualTo(42L);
    }
}
