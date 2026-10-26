package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoCoordinateTest {

    @ParameterizedTest
    @CsvSource({"-90, -180", "90, 180", "0, 0", "4.34, -74.35"})
    void aceptaCoordenadasDentroDeRango(double latitude, double longitude) {
        assertDoesNotThrow(() -> new GeoCoordinate(latitude, longitude));
    }

    @ParameterizedTest
    @CsvSource({"-90.1, 0", "90.1, 0"})
    void rechazaLatitudFueraDeRango(double latitude, double longitude) {
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(latitude, longitude));
    }

    @ParameterizedTest
    @CsvSource({"0, -180.1", "0, 180.1"})
    void rechazaLongitudFueraDeRango(double latitude, double longitude) {
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(latitude, longitude));
    }

    @Test
    void esIgualPorValorComoRecord() {
        assertEquals(new GeoCoordinate(4.34, -74.35), new GeoCoordinate(4.34, -74.35));
    }
}
