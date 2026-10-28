package com.fusaroute.domain.model;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PathProjectionTest {

    /** Tramo recto de ~1.1 km sobre el paralelo 4, que es la latitud de Fusagasuga. */
    private static final List<Coordinate> TRAMO_RECTO = List.of(
            new Coordinate(4.000, -74.000),
            new Coordinate(4.000, -74.010));

    @Test
    void proyecta_sobre_el_tramo_y_no_sobre_el_vertice_mas_cercano() {
        // El usuario esta justo al norte de la mitad del tramo. El punto de abordaje
        // correcto es el pie de la perpendicular (~111 m), no el vertice mas cercano
        // (~566 m), que es lo que daria medir solo contra los extremos.
        Coordinate usuario = new Coordinate(4.001, -74.005);

        BoardingPoint abordaje = PathProjection.nearestPointOn(TRAMO_RECTO, usuario).orElseThrow();

        assertThat(abordaje.point().latitude()).isCloseTo(4.000, Offset.offset(1e-9));
        assertThat(abordaje.point().longitude()).isCloseTo(-74.005, Offset.offset(1e-9));
        assertThat(abordaje.distanceInMeters()).isCloseTo(111.2, Offset.offset(1d));
    }

    @Test
    void si_la_perpendicular_cae_fuera_del_tramo_usa_el_extremo() {
        // Al este del primer vertice: la perpendicular caeria antes de que empiece el
        // tramo, asi que el punto mas cercano del tramo es ese vertice.
        Coordinate usuario = new Coordinate(4.000, -73.990);

        BoardingPoint abordaje = PathProjection.nearestPointOn(TRAMO_RECTO, usuario).orElseThrow();

        assertThat(abordaje.point()).isEqualTo(new Coordinate(4.000, -74.000));
    }

    @Test
    void elige_el_tramo_mas_cercano_cuando_el_trazado_tiene_varios() {
        List<Coordinate> trazadoEnL = List.of(
                new Coordinate(4.000, -74.000),
                new Coordinate(4.000, -74.010),
                new Coordinate(4.010, -74.010));

        // Cerca del tramo vertical (el segundo), lejos del horizontal.
        Coordinate usuario = new Coordinate(4.005, -74.009);

        BoardingPoint abordaje = PathProjection.nearestPointOn(trazadoEnL, usuario).orElseThrow();

        assertThat(abordaje.point().latitude()).isCloseTo(4.005, Offset.offset(1e-6));
        assertThat(abordaje.distanceInMeters()).isLessThan(150);
    }

    @Test
    void registra_cuanto_trazado_queda_detras_del_punto() {
        // El tramo mide ~1109 m; proyectando sobre su mitad, quedan ~555 m detras. Es
        // lo que permite medir el recorrido en buseta entre dos puntos de la ruta.
        Coordinate usuario = new Coordinate(4.001, -74.005);

        BoardingPoint abordaje = PathProjection.nearestPointOn(TRAMO_RECTO, usuario).orElseThrow();

        assertThat(abordaje.distanceAlongPathInMeters()).isCloseTo(554.7, Offset.offset(2d));
    }

    @Test
    void el_primer_vertice_no_tiene_trazado_detras() {
        BoardingPoint abordaje =
                PathProjection.nearestPointOn(TRAMO_RECTO, new Coordinate(4.000, -74.000)).orElseThrow();

        assertThat(abordaje.distanceAlongPathInMeters()).isZero();
    }

    @Test
    void una_ruta_sin_trazado_no_tiene_punto_de_abordaje() {
        // Vacio significa "no hay GeoJSON cargado", no "queda lejos". La ruta sigue
        // siendo buscable; es la distincion que sostiene la regla de descarte.
        Optional<BoardingPoint> abordaje =
                PathProjection.nearestPointOn(List.of(), new Coordinate(4.000, -74.000));

        assertThat(abordaje).isEmpty();
    }

    @Test
    void un_trazado_de_un_solo_punto_devuelve_ese_punto() {
        Coordinate unico = new Coordinate(4.000, -74.000);

        BoardingPoint abordaje =
                PathProjection.nearestPointOn(List.of(unico), new Coordinate(4.001, -74.000)).orElseThrow();

        assertThat(abordaje.point()).isEqualTo(unico);
        assertThat(abordaje.distanceInMeters()).isCloseTo(111.2, Offset.offset(1d));
    }

    @Test
    void un_vertice_repetido_no_rompe_el_calculo() {
        // Un GeoJSON dibujado a mano puede traer el mismo punto dos veces seguidas:
        // ese segmento tiene longitud cero y dividirlo reventaria.
        List<Coordinate> conVerticeRepetido = List.of(
                new Coordinate(4.000, -74.000),
                new Coordinate(4.000, -74.000),
                new Coordinate(4.000, -74.010));

        BoardingPoint abordaje =
                PathProjection.nearestPointOn(conVerticeRepetido, new Coordinate(4.001, -74.005)).orElseThrow();

        assertThat(abordaje.distanceInMeters()).isCloseTo(111.2, Offset.offset(1d));
    }
}
