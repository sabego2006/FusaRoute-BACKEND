package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinateTest {

    /** Un grado de latitud son ~111.195 m en cualquier punto del globo. */
    private static final double ONE_DEGREE_OF_LATITUDE_METERS = 111_195d;

    @Test
    void un_milesimo_de_grado_de_latitud_son_unos_111_metros() {
        Coordinate sur = new Coordinate(4.000, -74.000);
        Coordinate norte = new Coordinate(4.001, -74.000);

        assertThat(sur.distanceInMetersTo(norte))
                .isCloseTo(ONE_DEGREE_OF_LATITUDE_METERS / 1000, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    void la_distancia_a_si_mismo_es_cero() {
        Coordinate punto = new Coordinate(4.3433, -74.3661);

        assertThat(punto.distanceInMetersTo(punto)).isZero();
    }

    @Test
    void la_distancia_es_simetrica() {
        Coordinate centro = new Coordinate(4.3433, -74.3661);
        Coordinate pampa = new Coordinate(4.3300, -74.3600);

        assertThat(centro.distanceInMetersTo(pampa))
                .isCloseTo(pampa.distanceInMetersTo(centro), org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void un_grado_de_longitud_se_acorta_al_alejarse_del_ecuador() {
        // Misma diferencia de longitud, distinta latitud: cerca del polo mide menos.
        double enElTropico = new Coordinate(4.0, -74.0).distanceInMetersTo(new Coordinate(4.0, -74.01));
        double enLatitudAlta = new Coordinate(60.0, -74.0).distanceInMetersTo(new Coordinate(60.0, -74.01));

        assertThat(enLatitudAlta).isLessThan(enElTropico);
    }
}
