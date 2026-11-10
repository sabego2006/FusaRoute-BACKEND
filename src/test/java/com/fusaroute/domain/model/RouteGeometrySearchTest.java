package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Geometria de la busqueda (P1): pasadas, tramo recorrido, circulos y caminata aproximada. */
class RouteGeometrySearchTest {

    private static final double LAT = 4.34;

    /** Recta este-oeste, vertice cada 0,005 grados (~555 m), de -74.40 a -74.34. */
    private static List<Coordinate> east() {
        List<Coordinate> path = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            path.add(new Coordinate(LAT, -74.40 + i * 0.005));
        }
        return path;
    }

    private static Route route(List<Coordinate> path) {
        return new Route(1L, "Prueba", null, RouteType.URBANA, RouteStatus.ACTIVA, List.of(), List.of(), path);
    }

    @Test
    void has_path_exige_al_menos_dos_puntos() {
        assertThat(route(List.of()).hasPath()).isFalse();
        assertThat(route(List.of(new Coordinate(LAT, -74.4))).hasPath()).isFalse();
        assertThat(route(east()).hasPath()).isTrue();
    }

    @Test
    void match_between_devuelve_vacio_sin_trazado_o_sin_pasada_cercana() {
        Coordinate a = new Coordinate(LAT, -74.39);
        Coordinate b = new Coordinate(LAT, -74.35);

        assertThat(route(List.of()).matchBetween(a, b, 800)).isEmpty();
        assertThat(route(east()).matchBetween(new Coordinate(4.40, -74.39), b, 800)).isEmpty();
        assertThat(route(east()).matchBetween(a, new Coordinate(4.40, -74.35), 800)).isEmpty();
    }

    @Test
    void match_between_exige_abordaje_antes_que_bajada() {
        Coordinate west = new Coordinate(LAT, -74.39);
        Coordinate eastPoint = new Coordinate(LAT, -74.35);

        assertThat(route(east()).matchBetween(west, eastPoint, 800)).isPresent();
        assertThat(route(east()).matchBetween(eastPoint, west, 800)).isEmpty();
    }

    @Test
    void el_tramo_en_buseta_debe_ser_de_al_menos_50_m() {
        Coordinate a = new Coordinate(LAT, -74.3900);
        // 0,0002 grados ~ 22 m mas al este; con umbral 0 solo hay una pasada por punto.
        Coordinate b = new Coordinate(LAT, -74.3898);

        assertThat(route(east()).matchBetween(a, b, 1)).isEmpty();
    }

    @Test
    void entre_varias_pasadas_gana_la_combinacion_de_menor_caminata_total() {
        // Va hacia el este, sube 0,02 grados (~2,2 km) y regresa hacia el oeste unos 300 m mas al norte.
        List<Coordinate> lazo = new ArrayList<>(east());
        lazo.add(new Coordinate(LAT + 0.0027, -74.34));
        for (int i = 11; i >= 0; i--) {
            lazo.add(new Coordinate(LAT + 0.0027, -74.40 + i * 0.005));
        }
        Route route = route(lazo);
        // Origen entre las dos pasadas (mas cerca de la de ida), destino mas cerca de la de vuelta.
        Coordinate origin = new Coordinate(LAT + 0.0005, -74.38);
        Coordinate destination = new Coordinate(LAT + 0.0022, -74.37);

        RouteMatch match = route.matchBetween(origin, destination, 800).orElseThrow();

        assertThat(match.boarding().distanceInMeters()).isLessThan(100);
        assertThat(match.dropOff().distanceInMeters()).isLessThan(100);
        assertThat(match.boarding().distanceAlongPathInMeters())
                .isLessThan(match.dropOff().distanceAlongPathInMeters());
    }

    @Test
    void la_distancia_de_recorrido_y_la_caminata_total_salen_del_match() {
        RouteMatch match = route(east()).matchBetween(
                new Coordinate(LAT + 0.0009, -74.39), new Coordinate(LAT + 0.0018, -74.35), 800).orElseThrow();

        // 0,04 grados de longitud a esta latitud ~ 4,43 km.
        assertThat(match.rideDistanceInMeters()).isBetween(4_350d, 4_500d);
        double straight = match.boarding().distanceInMeters() + match.dropOff().distanceInMeters();
        assertThat(match.walkingDistanceInMeters()).isEqualTo(straight * 1.3);
    }

    @Test
    void path_between_incluye_los_puntos_exactos_de_los_extremos() {
        Route route = route(east());
        double from = 700;
        double to = 2_900;

        List<Coordinate> tramo = route.pathBetween(from, to);

        assertThat(tramo.size()).isGreaterThanOrEqualTo(2);
        assertThat(tramo.get(0).distanceInMetersTo(new Coordinate(LAT, -74.40))).isBetween(690d, 710d);
        assertThat(tramo.get(tramo.size() - 1).distanceInMetersTo(new Coordinate(LAT, -74.40))).isBetween(2_890d, 2_910d);
    }

    @Test
    void path_between_vacio_si_el_rango_esta_invertido_o_no_hay_trazado() {
        assertThat(route(east()).pathBetween(500, 100)).isEmpty();
        assertThat(route(List.of()).pathBetween(0, 100)).isEmpty();
    }

    @Test
    void distance_to_path_da_la_distancia_recta_y_vacio_sin_trazado() {
        assertThat(route(east()).distanceToPathInMeters(new Coordinate(LAT + 0.0045, -74.38)).getAsDouble())
                .isBetween(480d, 520d);
        assertThat(route(List.of()).distanceToPathInMeters(new Coordinate(LAT, -74.38))).isEmpty();
    }

    // --- Caminata aproximada

    @Test
    void la_caminata_es_la_distancia_recta_por_1_3_y_los_minutos_suben_al_entero() {
        BoardingPoint point = new BoardingPoint(new Coordinate(LAT, -74.38), 500, 0);

        assertThat(point.walkingDistanceInMeters()).isEqualTo(650d);
        // 650 m a 5 km/h = 7,8 min -> 8.
        assertThat(point.walkingMinutesCeil()).isEqualTo(8);
        assertThat(point.approxWalkingTime().toSeconds()).isEqualTo(468);
    }

    // --- Circulos de congestion

    private static TrafficSector circle(Double lat, Double lng, Integer radius) {
        return new TrafficSector(1L, "Indio (tarde)", Set.of("Indio"), LocalTime.of(17, 0), LocalTime.of(19, 0),
                lat, lng, radius);
    }

    @Test
    void un_sector_con_circulo_se_cruza_si_el_tramo_pasa_a_menos_del_radio() {
        TrafficSector indio = circle(LAT + 0.0030, -74.375, 450); // centro ~330 m al norte del trazado

        assertThat(indio.hasCircle()).isTrue();
        assertThat(indio.isCrossedBy(east())).isTrue();
        assertThat(indio.isCrossedBy(east().subList(0, 3))).isFalse(); // solo hasta -74.39
        assertThat(indio.isCrossedBy(List.of())).isFalse();
    }

    @Test
    void el_radio_es_inclusivo_y_un_sector_sin_circulo_nunca_se_cruza() {
        Coordinate onPath = new Coordinate(LAT, -74.375);
        // Centro a ~222 m (0,002 grados de latitud) del trazado.
        TrafficSector ajustado = circle(LAT + 0.002, -74.375, 223);
        TrafficSector corto = circle(LAT + 0.002, -74.375, 200);

        assertThat(ajustado.isCrossedBy(east())).isTrue();
        assertThat(corto.isCrossedBy(east())).isFalse();
        assertThat(new TrafficSector(2L, "Sin circulo", Set.of("X"), LocalTime.of(7, 0), LocalTime.of(9, 0))
                .isCrossedBy(List.of(onPath, new Coordinate(LAT, -74.37)))).isFalse();
        assertThat(ajustado.center()).isEqualTo(new Coordinate(LAT + 0.002, -74.375));
    }

    @Test
    void el_circulo_se_define_completo_o_no_se_define() {
        assertThatThrownBy(() -> circle(LAT, null, 450)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> circle(LAT, -74.375, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> circle(LAT, -74.375, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(circle(null, null, null).hasCircle()).isFalse();
    }

    // --- Lugares

    @Test
    void un_lugar_responde_a_su_nombre_y_a_sus_alias_sin_tildes_ni_mayusculas() {
        Place indio = new Place(1L, "monumento-el-indio", "Monumento El Indio",
                new Coordinate(4.33, -74.37), List.of("Indio"));

        assertThat(indio.matchesName("monumento EL indio")).isTrue();
        assertThat(indio.matchesName("ÍNDIO")).isTrue();
        assertThat(indio.matchesName("Indio Grande")).isFalse();
        assertThat(indio.matchesName(" ")).isFalse();
        assertThat(indio.matchesName(null)).isFalse();
        assertThat(indio.allNames()).containsExactly("Monumento El Indio", "Indio");
    }

    @Test
    void el_resultado_resuelto_expone_si_tiene_coordenada_y_lugar() {
        Place place = new Place(1L, "a", "A", new Coordinate(4.33, -74.37), List.of());

        assertThat(new ResolvedPoint(place.coordinate(), "A", place).findPlace()).contains(place);
        assertThat(new ResolvedPoint(null, "Villa Leny", null).hasCoordinate()).isFalse();
        assertThat(new ResolvedPoint(null, "Villa Leny", null).findPlace()).isEmpty();
    }

    @Test
    void un_resultado_de_busqueda_sin_datos_nuevos_tiene_valores_por_defecto_seguros() {
        RouteSearchResult result = new RouteSearchResult(route(east()), null, null, null, null);

        assertThat(result.etaSource()).isEqualTo(EtaSource.ESTIMATED);
        assertThat(result.longWalk()).isFalse();
        assertThat(result.congestionSectors()).isEmpty();
        assertThat(result.origin()).isNull();
    }
}
