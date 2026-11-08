package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.InvalidFavoriteDestinationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validaciones de {@link FavoriteDestination} (RF-10, SCRUM-21).
 *
 * Es un record de dominio puro, asi que el test no levanta Spring ni usa Mockito:
 * {@code TestArchitectureTest} lo prohibe para {@code domain..} y {@code application..}.
 */
class FavoriteDestinationTest {

    private static final Instant MARCADO = Instant.parse("2026-11-04T09:15:00Z");

    @Test
    void guarda_el_barrio_tal_como_se_eligio() {
        FavoriteDestination favorito = new FavoriteDestination(7L, "Maiz Amarillo", MARCADO);

        assertThat(favorito.userId()).isEqualTo(7L);
        assertThat(favorito.destination()).isEqualTo("Maiz Amarillo");
        assertThat(favorito.savedAt()).isEqualTo(MARCADO);
    }

    @Test
    void el_favorito_siempre_pertenece_a_un_usuario() {
        assertThatThrownBy(() -> new FavoriteDestination(null, "Centro", MARCADO))
                .isInstanceOf(InvalidFavoriteDestinationException.class)
                .hasMessageContaining("pertenece a un usuario");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void el_destino_no_puede_ir_vacio(String destinoInvalido) {
        assertThatThrownBy(() -> new FavoriteDestination(7L, destinoInvalido, MARCADO))
                .isInstanceOf(InvalidFavoriteDestinationException.class)
                .hasMessageContaining("no puede ir vacio");
    }

    @Test
    void el_favorito_necesita_una_fecha() {
        assertThatThrownBy(() -> new FavoriteDestination(7L, "Centro", null))
                .isInstanceOf(InvalidFavoriteDestinationException.class)
                .hasMessageContaining("fecha");
    }

    @Test
    void el_barrio_se_guarda_sin_espacios_sobrantes() {
        // Sin el trim, "  Centro " no coincidiria con el "Centro" del catalogo y la
        // precarga del campo de busqueda quedaria sin efecto.
        FavoriteDestination favorito = new FavoriteDestination(7L, "  Maiz Amarillo ", MARCADO);

        assertThat(favorito.destination()).isEqualTo("Maiz Amarillo");
    }

    @Test
    void un_destino_mas_largo_que_la_columna_se_rechaza_en_vez_de_recortarse() {
        // Se rechaza, no se recorta: un nombre cortado a la mitad no coincide con
        // ningun barrio y la precarga quedaria inservible. Ademas, sin esta
        // validacion el valor llegaria a la base y reventaria el INSERT con un 500
        // en vez de un 400 que el frontend pueda mostrar.
        String demasiadoLargo = "x".repeat(FavoriteDestination.MAX_DESTINATION_LENGTH + 1);

        assertThatThrownBy(() -> new FavoriteDestination(7L, demasiadoLargo, MARCADO))
                .isInstanceOf(InvalidFavoriteDestinationException.class)
                .hasMessageContaining("120");
    }

    @Test
    void un_destino_del_largo_exacto_de_la_columna_se_acepta() {
        String justo = "x".repeat(FavoriteDestination.MAX_DESTINATION_LENGTH);

        assertThatCode(() -> new FavoriteDestination(7L, justo, MARCADO)).doesNotThrowAnyException();
    }

    @Test
    void el_largo_se_mide_despues_de_quitar_espacios() {
        // Los espacios no llegan a la columna, asi que no deben contar para el tope:
        // rechazar por culpa de ellos seria rechazar un nombre que si cabe.
        String conEspacios = "  " + "x".repeat(FavoriteDestination.MAX_DESTINATION_LENGTH) + "  ";

        assertThatCode(() -> new FavoriteDestination(7L, conEspacios, MARCADO)).doesNotThrowAnyException();
    }
}
