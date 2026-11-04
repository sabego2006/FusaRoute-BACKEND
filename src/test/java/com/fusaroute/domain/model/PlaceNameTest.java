package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los casos de esta clase no son inventados: todos salen de pares que de verdad
 * conviven en la semilla y que antes de SCRUM-184 no se cruzaban en la busqueda.
 */
class PlaceNameTest {

    @Test
    void la_diferencia_de_mayusculas_de_la_semilla_deja_de_importar() {
        // V12 sembro estos dos nombres en rutas distintas, para el mismo sitio.
        assertThat(PlaceName.normalize("Terminal de Transporte"))
                .isEqualTo(PlaceName.normalize("TERMINAL DE TRANSPORTE"));
        assertThat(PlaceName.normalize("Centro Comercial Avenida"))
                .isEqualTo(PlaceName.normalize("Centro comercial avenida"));
    }

    @Test
    void las_tildes_dejan_de_importar() {
        assertThat(PlaceName.normalize("Maíz Amarillo")).isEqualTo("maiz amarillo");
        assertThat(PlaceName.normalize("Fusagasugá")).isEqualTo("fusagasuga");
        assertThat(PlaceName.normalize("Jaibaná")).isEqualTo("jaibana");
    }

    @Test
    void los_puntos_de_las_abreviaturas_dejan_de_importar() {
        // El campo escribio 'Cra. 6' en una ruta y 'Cra 6' en otra.
        assertThat(PlaceName.normalize("Cra. 6")).isEqualTo(PlaceName.normalize("Cra 6"));
        assertThat(PlaceName.normalize("U. Hospital Éxito")).isEqualTo("u hospital exito");
    }

    @Test
    void los_espacios_de_sobra_se_colapsan_y_se_recortan() {
        assertThat(PlaceName.normalize("  Plaza   de  Mercado ")).isEqualTo("plaza de mercado");
    }

    @Test
    void nulo_devuelve_cadena_vacia_y_no_revienta() {
        // La cadena vacia no coincide con ningun lugar real, asi que un parametro
        // perdido se convierte en "no hay ruta", no en un 500.
        assertThat(PlaceName.normalize(null)).isEmpty();
    }

    @Test
    void dos_lugares_distintos_siguen_siendo_distintos() {
        // La normalizacion es agresiva, pero no funde sitios que no son el mismo.
        assertThat(PlaceName.normalize("Ebenezer")).isNotEqualTo(PlaceName.normalize("Ciudad Ebenezer"));
        assertThat(PlaceName.normalize("Chinauta")).isNotEqualTo(PlaceName.normalize("Colegio Chinauta"));
    }
}
