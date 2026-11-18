package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Barrios del TRAMO que la persona recorre (SCRUM-198), no de la ruta completa.
 *
 * El caso que da nombre a la historia es {@link #ruta_abcde_subiendo_en_c_y_bajando_en_e}: una ruta
 * A-B-C-D-E con subida en C y bajada en E muestra C, D, E, y nunca A ni B.
 */
class RouteTripSegmentTest {

    private static final double LAT = 4.34;

    /**
     * Recta oriente-occidente con un vertice cada 0,005 grados (~555 m), de -74.40 a -74.34: 12
     * segmentos iguales. Con un trazado de segmentos iguales, la fraccion del recorrido y la
     * distancia acumulada son proporcionales, y las cuentas del tramo se pueden escribir a mano.
     */
    private static List<Coordinate> straightPath() {
        List<Coordinate> path = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            path.add(new Coordinate(LAT, -74.40 + i * 0.005));
        }
        return path;
    }

    /** Largo total del trazado recto, medido con la misma formula del dominio. */
    private static double totalLength(List<Coordinate> path) {
        double total = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += path.get(i).distanceInMetersTo(path.get(i + 1));
        }
        return total;
    }

    private static Route routeWith(List<String> neighborhoods, List<Coordinate> path) {
        return new Route(1L, "Prueba", null, RouteType.URBANA, RouteStatus.ACTIVA,
                neighborhoods, List.of(), path);
    }

    private static final List<String> ABCDE = List.of("A", "B", "C", "D", "E");

    @Test
    void ruta_abcde_subiendo_en_c_y_bajando_en_e() {
        List<Coordinate> path = straightPath();
        Route route = routeWith(ABCDE, path);
        double total = totalLength(path);

        // Con 5 barrios, cada uno cae a 0, 1/4, 2/4, 3/4 y 4/4 del recorrido: C esta en la mitad.
        List<String> tramo = route.neighborhoodsBetween(total * 0.5, total);

        assertThat(tramo).containsExactly("C", "D", "E");
        assertThat(tramo).doesNotContain("A", "B");
    }

    @Test
    void un_barrio_antes_de_la_subida_nunca_entra_aunque_quede_cerca() {
        List<Coordinate> path = straightPath();
        Route route = routeWith(ABCDE, path);
        double total = totalLength(path);

        // Subida un metro DESPUES de la altura de C: el rango es estricto hacia atras, asi que C sale.
        assertThat(route.neighborhoodsBetween(total * 0.5 + 1, total)).containsExactly("D", "E");
    }

    @Test
    void subida_igual_a_bajada_no_es_un_viaje_pero_responde_sin_inventar() {
        List<Coordinate> path = straightPath();
        Route route = routeWith(ABCDE, path);
        double total = totalLength(path);

        // Justo a la altura de C: el rango cerrado lo incluye y no hay nada mas que mostrar.
        assertThat(route.neighborhoodsBetween(total * 0.5, total * 0.5)).containsExactly("C");
        // Entre dos barrios: el tramo existe pero no cubre ninguno. Lista vacia, no un error.
        assertThat(route.neighborhoodsBetween(total * 0.6, total * 0.6)).isEmpty();
    }

    @Test
    void bajada_antes_que_la_subida_no_da_tramo() {
        List<Coordinate> path = straightPath();
        Route route = routeWith(ABCDE, path);
        double total = totalLength(path);

        // Una ruta es un solo sentido: si la bajada queda antes, no hay viaje que describir.
        assertThat(route.neighborhoodsBetween(total * 0.8, total * 0.2)).isEmpty();
    }

    @Test
    void sin_trazado_no_hay_tramo() {
        assertThat(routeWith(ABCDE, List.of()).neighborhoodsBetween(0, 1_000)).isEmpty();
        assertThat(routeWith(ABCDE, List.of(new Coordinate(LAT, -74.4))).neighborhoodsBetween(0, 1_000))
                .isEmpty();
    }

    @Test
    void sin_barrios_cargados_no_hay_tramo() {
        List<Coordinate> path = straightPath();
        assertThat(routeWith(List.of(), path).neighborhoodsBetween(0, totalLength(path))).isEmpty();
    }

    @Test
    void trazado_degenerado_con_todos_los_vertices_en_el_mismo_punto() {
        Coordinate same = new Coordinate(LAT, -74.4);
        Route route = routeWith(ABCDE, List.of(same, same, same));

        // Largo total cero: no hay recorrido que repartir entre los barrios.
        assertThat(route.neighborhoodsBetween(0, 0)).isEmpty();
    }

    @Test
    void un_solo_barrio_cae_al_principio_del_recorrido() {
        List<Coordinate> path = straightPath();
        Route route = routeWith(List.of("Unico"), path);
        double total = totalLength(path);

        assertThat(route.neighborhoodsBetween(0, total)).containsExactly("Unico");
        // Subiendo despues del principio ya no se nombra: el unico barrio queda atras.
        assertThat(route.neighborhoodsBetween(total * 0.1, total)).isEmpty();
    }

    @Test
    void nombres_repetidos_consecutivos_se_colapsan() {
        List<Coordinate> path = straightPath();
        // Deriva de los datos de campo: el mismo nombre dos veces seguidas no son dos pasadas.
        Route route = routeWith(List.of("A", "Centro", "Centro", "D"), path);

        assertThat(route.neighborhoodsBetween(0, totalLength(path))).containsExactly("A", "Centro", "D");
    }

    @Test
    void ruta_circular_vuelve_a_nombrar_el_barrio_al_que_regresa() {
        // Cuadrado cerrado: el recorrido vuelve al punto de partida.
        List<Coordinate> loop = List.of(
                new Coordinate(4.34, -74.40), new Coordinate(4.35, -74.40),
                new Coordinate(4.35, -74.39), new Coordinate(4.34, -74.39),
                new Coordinate(4.34, -74.40));
        Route route = routeWith(List.of("Centro", "Norte", "Oriente", "Sur", "Centro"), loop);
        double total = totalLength(loop);

        // Un nombre que reaparece mas adelante SI se repite: ahi la buseta vuelve a pasar.
        assertThat(route.neighborhoodsBetween(0, total))
                .containsExactly("Centro", "Norte", "Oriente", "Sur", "Centro");
        // Y media vuelta solo nombra su mitad, con un solo "Centro".
        assertThat(route.neighborhoodsBetween(0, total * 0.5)).containsExactly("Centro", "Norte", "Oriente");
    }

    @Test
    void el_tramo_sale_del_match_entre_dos_puntos() {
        List<Coordinate> path = straightPath();
        Route route = routeWith(ABCDE, path);

        // Origen a la altura de C (mitad del recorrido) y destino en la punta oriental.
        RouteMatch match = route.matchBetween(new Coordinate(LAT, -74.37), new Coordinate(LAT, -74.34), 800)
                .orElseThrow();

        assertThat(match.tripNeighborhoods()).containsExactly("C", "D", "E");
        // Y el trazado del tramo empieza donde se sube, no en la punta occidental de la ruta.
        assertThat(match.ridePath().get(0).longitude()).isGreaterThan(-74.38);
        assertThat(match.ridePath()).hasSizeLessThan(path.size());
    }

    @Test
    void sin_trazado_el_resultado_de_busqueda_lleva_el_tramo_desconocido() {
        Route route = routeWith(ABCDE, List.of());
        RouteSearchResult result = new RouteSearchResult(route, null, null, null, null);

        // Nunca null: quien lo muestre no tiene que distinguir "no hay tramo" de "tramo vacio".
        assertThat(result.tripSegment()).isEqualTo(TripSegment.UNKNOWN);
        assertThat(result.tripSegment().hasRidePath()).isFalse();
        assertThat(result.tripSegment().neighborhoods()).isEmpty();
        assertThat(result.tripSegment().boardingWalk().findReference()).isEmpty();
        assertThat(result.tripSegment().boardingWalk().bearing()).isEmpty();
    }

    @Test
    void el_rumbo_se_calla_cuando_los_dos_puntos_son_el_mismo_sitio() {
        Coordinate origin = new Coordinate(LAT, -74.40);

        // Caminata de ~0 m: no hay direccion que tenga sentido dar.
        assertThat(origin.bearingDegreesTo(new Coordinate(LAT, -74.40))).isEmpty();
        assertThat(origin.bearingDegreesTo(new Coordinate(LAT + 0.00005, -74.40))).isEmpty();
    }

    @Test
    void el_rumbo_apunta_al_norte_al_oriente_al_sur_y_al_occidente() {
        Coordinate center = new Coordinate(LAT, -74.40);

        assertThat(center.bearingDegreesTo(new Coordinate(LAT + 0.01, -74.40)).getAsDouble())
                .isCloseTo(0, org.assertj.core.data.Offset.offset(1d));
        assertThat(center.bearingDegreesTo(new Coordinate(LAT, -74.39)).getAsDouble())
                .isCloseTo(90, org.assertj.core.data.Offset.offset(1d));
        assertThat(center.bearingDegreesTo(new Coordinate(LAT - 0.01, -74.40)).getAsDouble())
                .isCloseTo(180, org.assertj.core.data.Offset.offset(1d));
        assertThat(center.bearingDegreesTo(new Coordinate(LAT, -74.41)).getAsDouble())
                .isCloseTo(270, org.assertj.core.data.Offset.offset(1d));
    }

    @Test
    void la_referencia_de_la_caminata_es_el_lugar_mas_cercano_y_solo_si_esta_cerca() {
        List<Place> places = List.of(
                new Place(1L, "cerca", "Panaderia Filipo", new Coordinate(LAT, -74.4000), List.of()),
                new Place(2L, "lejos", "Terminal", new Coordinate(LAT, -74.3000), List.of()));

        // A 20 m del primero: lo nombra.
        assertThat(Place.nearestTo(places, new Coordinate(LAT + 0.00018, -74.4000),
                TripSegment.REFERENCE_RADIUS_METERS).map(Place::name)).contains("Panaderia Filipo");
        // A ~2 km de todo: no nombra ninguno antes que mandar a la persona a otra esquina.
        assertThat(Place.nearestTo(places, new Coordinate(LAT + 0.02, -74.4000),
                TripSegment.REFERENCE_RADIUS_METERS)).isEmpty();
        // Sin catalogo tampoco inventa.
        assertThat(Place.nearestTo(List.of(), new Coordinate(LAT, -74.4), 800)).isEmpty();
    }
}
