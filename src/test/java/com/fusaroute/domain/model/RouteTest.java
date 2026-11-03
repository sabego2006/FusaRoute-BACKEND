package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    private static Fare fare(String point, int amount, int order, String validFrom) {
        return new Fare(point, BigDecimal.valueOf(amount), order, LocalDate.parse(validFrom));
    }

    private static Route route(RouteType type, RouteStatus status, List<Fare> fares) {
        return new Route(1L, "Ruta", "RUTA", type, status, List.of("A", "B"), fares, List.of());
    }

    /** Ruta cuyo trazado es un tramo recto de ~1.1 km sobre el paralelo 4. */
    private static Route routeConTrazado() {
        return new Route(1L, "Ruta", "RUTA", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("A", "B"), List.of(),
                List.of(new Coordinate(4.000, -74.000), new Coordinate(4.000, -74.010)));
    }

    @Test
    void la_ultima_milla_sale_del_trazado_propio() {
        Coordinate usuario = new Coordinate(4.001, -74.005);

        BoardingPoint abordaje = routeConTrazado().boardingPointFor(usuario).orElseThrow();

        assertThat(abordaje.distanceInMeters()).isLessThan(BoardingPoint.MAX_WALKING_DISTANCE_METERS);
    }

    @Test
    void una_ruta_sin_trazado_no_tiene_ultima_milla() {
        Route sinTrazado = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of());

        assertThat(sinTrazado.boardingPointFor(new Coordinate(4.000, -74.000))).isEmpty();
    }

    @Test
    void el_recorrido_en_buseta_se_mide_sobre_el_trazado_entre_las_dos_proyecciones() {
        // Sube en el 20% del tramo y se baja en el 80%: viaja el 60% de sus ~1109 m.
        // No cuenta el trazado que la ruta recorre antes de que suba ni despues.
        double recorrido = routeConTrazado().travelDistanceInMeters(
                new Coordinate(4.001, -74.002), new Coordinate(4.001, -74.008)).orElseThrow();

        assertThat(recorrido).isCloseTo(665.6, org.assertj.core.data.Offset.offset(2d));
    }

    @Test
    void una_ruta_sin_trazado_no_tiene_recorrido_medible() {
        Route sinTrazado = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of());

        assertThat(sinTrazado.travelDistanceInMeters(
                new Coordinate(4.000, -74.000), new Coordinate(4.000, -74.010))).isEmpty();
    }

    @Test
    void se_descarta_la_ruta_que_obliga_a_caminar_mas_de_ochocientos_metros() {
        // ~1.1 km al norte del tramo: la buseta pasa "cerca" en el mapa, pero la
        // caminata ya no compensa.
        Coordinate lejos = new Coordinate(4.010, -74.005);

        assertThat(routeConTrazado().isWithinWalkingDistance(lejos, new Coordinate(4.000, -74.005)))
                .isFalse();
    }

    @Test
    void se_acepta_la_ruta_cuando_ambas_puntas_quedan_cerca() {
        assertThat(routeConTrazado().isWithinWalkingDistance(
                new Coordinate(4.001, -74.002), new Coordinate(4.001, -74.008)))
                .isTrue();
    }

    @Test
    void una_ruta_sin_trazado_nunca_se_descarta_por_distancia_a_pie() {
        // Hoy 7 de las 9 rutas sembradas no tienen GeoJSON. Descartarlas las haria
        // invisibles por un dato que falta, no por una razon de negocio.
        Route sinTrazado = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of());

        assertThat(sinTrazado.isWithinWalkingDistance(
                new Coordinate(4.900, -74.900), new Coordinate(5.900, -75.900)))
                .isTrue();
    }

    // --- SCRUM-130: punto derivado del nombre del barrio, sin coordenadas del cliente ---

    @Test
    void el_primer_barrio_cae_al_inicio_del_trazado() {
        // routeConTrazado() tiene barrios ["A", "B"] y un trazado de 2 puntos: "A" es
        // el indice 0, que corresponde a la fraccion 0 del recorrido.
        Coordinate punto = routeConTrazado().pointAtNeighborhood("A").orElseThrow();

        assertThat(punto).isEqualTo(new Coordinate(4.000, -74.000));
    }

    @Test
    void el_ultimo_barrio_cae_al_final_del_trazado() {
        Coordinate punto = routeConTrazado().pointAtNeighborhood("B").orElseThrow();

        assertThat(punto).isEqualTo(new Coordinate(4.000, -74.010));
    }

    @Test
    void un_barrio_intermedio_cae_a_su_fraccion_del_recorrido() {
        // Trazado recto de 3 barrios en orden: "B" (indice 1 de 3) cae a la mitad.
        Route tresBarrios = new Route(1L, "Ruta", "RUTA", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("A", "B", "C"), List.of(),
                List.of(new Coordinate(4.000, -74.000), new Coordinate(4.000, -74.010)));

        Coordinate punto = tresBarrios.pointAtNeighborhood("B").orElseThrow();

        assertThat(punto.longitude()).isCloseTo(-74.005, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void un_barrio_que_no_esta_en_la_ruta_no_tiene_punto() {
        assertThat(routeConTrazado().pointAtNeighborhood("Z")).isEmpty();
    }

    @Test
    void una_ruta_sin_trazado_no_puede_derivar_el_punto_de_ningun_barrio() {
        Route sinTrazado = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of());

        assertThat(sinTrazado.pointAtNeighborhood("A")).isEmpty();
    }

    @Test
    void sin_coordenada_del_cliente_en_ninguna_punta_el_filtro_de_distancia_no_aplica() {
        // El overload que solo mira lo que el cliente mando: con las dos puntas
        // vacias, no hay nada que medir, asi que pasa sin importar la geometria real.
        assertThat(routeConTrazado().isWithinWalkingDistance(Optional.empty(), Optional.empty()))
                .isTrue();
    }

    @Test
    void con_coordenada_del_cliente_solo_en_una_punta_esa_punta_si_se_evalua() {
        // La punta con coordenada del cliente, a ~1.1 km del trazado, si se mide y
        // descarta; si la otra punta fuera la unica mandada (cerca), pasaria.
        Coordinate lejos = new Coordinate(4.010, -74.005);

        assertThat(routeConTrazado().isWithinWalkingDistance(Optional.of(lejos), Optional.empty()))
                .isFalse();
        assertThat(routeConTrazado().isWithinWalkingDistance(
                Optional.empty(), Optional.of(new Coordinate(4.001, -74.005))))
                .isTrue();
    }

    @Test
    void tarifa_urbana_unica_con_punto_null_se_conserva() {
        Route route = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of(fare(null, 2600, 0, "2026-02-05")));

        assertThat(route.withCurrentFares(TODAY).fares()).hasSize(1);
        assertThat(route.withCurrentFares(TODAY).fares().get(0).amount()).isEqualByComparingTo("2600");
    }

    @Test
    void por_cada_punto_gana_la_fila_mas_reciente() {
        Route route = route(RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA, List.of(
                fare("Alaska", 3300, 3, "2025-01-16"),
                fare("Alaska", 3550, 0, "2026-02-05")));

        List<Fare> current = route.withCurrentFares(TODAY).fares();

        assertThat(current).hasSize(1);
        assertThat(current.get(0).amount()).isEqualByComparingTo("3550");
        assertThat(current.get(0).validFrom()).isEqualTo(LocalDate.of(2026, 2, 5));
    }

    @Test
    void la_fila_mas_reciente_gana_sin_importar_el_orden_de_llegada() {
        Route route = route(RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA, List.of(
                fare("Alaska", 3550, 0, "2026-02-05"),
                fare("Alaska", 3300, 3, "2025-01-16")));

        assertThat(route.withCurrentFares(TODAY).fares().get(0).amount()).isEqualByComparingTo("3550");
    }

    @Test
    void una_tarifa_futura_no_rige_y_se_usa_la_anterior() {
        Route route = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of(
                fare(null, 2600, 0, "2026-02-05"),
                fare(null, 2800, 0, "2026-09-28")));

        assertThat(route.withCurrentFares(TODAY).fares().get(0).amount()).isEqualByComparingTo("2600");
    }

    @Test
    void una_tarifa_que_empieza_hoy_ya_rige() {
        Route route = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of(
                fare(null, 2600, 0, "2026-02-05"),
                fare(null, 2800, 0, "2026-09-27")));

        assertThat(route.withCurrentFares(TODAY).fares().get(0).amount()).isEqualByComparingTo("2800");
    }

    @Test
    void si_todas_son_futuras_no_queda_ninguna() {
        Route route = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of(fare(null, 2800, 0, "2026-10-01")));

        assertThat(route.withCurrentFares(TODAY).fares()).isEmpty();
    }

    @Test
    void series_de_vigencia_mezcladas_salen_de_menor_a_mayor_precio() {
        // Caso Fusagasuga - Pasca (V2 + V3): dos series con order_index solapados.
        Route route = route(RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA, List.of(
                fare("Alaska", 3550, 0, "2026-02-05"),
                fare("Corregimiento", 3000, 0, "2025-01-16"),
                fare("Alaska", 3300, 3, "2025-01-16"),
                fare("La Capilla", 3600, 6, "2025-01-16"),
                fare("Pasca", 4300, 7, "2025-01-16")));

        List<Fare> current = route.withCurrentFares(TODAY).fares();

        assertThat(current).extracting(Fare::referencePoint)
                .containsExactly("Corregimiento", "Alaska", "La Capilla", "Pasca");
        assertThat(current).extracting(f -> f.amount().intValue())
                .containsExactly(3000, 3550, 3600, 4300);
    }

    @Test
    void precios_iguales_se_desempatan_por_order_index() {
        Route route = route(RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA, List.of(
                fare("Chinauta (retorno antes del peaje)", 5000, 2, "2026-02-05"),
                fare("Colegio Chinauta", 5000, 1, "2026-02-05")));

        assertThat(route.withCurrentFares(TODAY).fares()).extracting(Fare::referencePoint)
                .containsExactly("Colegio Chinauta", "Chinauta (retorno antes del peaje)");
    }

    @Test
    void conserva_identidad_barrios_en_orden_y_estado() {
        Route route = route(RouteType.URBANA, RouteStatus.SUSPENDIDA, List.of());

        Route current = route.withCurrentFares(TODAY);

        assertThat(current.id()).isEqualTo(1L);
        assertThat(current.neighborhoods()).containsExactly("A", "B");
        assertThat(current.isActive()).isFalse();
        assertThat(route(RouteType.URBANA, RouteStatus.ACTIVA, List.of()).isActive()).isTrue();
    }

    @Test
    void sin_tarifas_la_ultima_actualizacion_queda_vacia() {
        Route route = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of());

        assertThat(route.lastFareUpdate()).isEmpty();
    }

    @Test
    void con_una_tarifa_la_ultima_actualizacion_es_su_validFrom() {
        Route route = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of(fare(null, 2600, 0, "2026-02-05")));

        assertThat(route.lastFareUpdate()).isEqualTo(Optional.of(LocalDate.of(2026, 2, 5)));
    }

    @Test
    void con_varias_tarifas_la_ultima_actualizacion_es_el_validFrom_mas_reciente() {
        Route route = route(RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA, List.of(
                fare("Alaska", 3550, 0, "2026-02-05"),
                fare("Pasca", 4300, 7, "2025-01-16")));

        assertThat(route.lastFareUpdate()).isEqualTo(Optional.of(LocalDate.of(2026, 2, 5)));
    }

    @Test
    void la_ultima_actualizacion_ignora_tarifas_futuras_si_se_pide_tras_filtrar_vigentes() {
        Route route = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of(
                fare(null, 2600, 0, "2026-02-05"),
                fare(null, 2800, 0, "2026-09-28")));

        Route vigente = route.withCurrentFares(TODAY);

        assertThat(vigente.lastFareUpdate()).isEqualTo(Optional.of(LocalDate.of(2026, 2, 5)));
        assertThat(route.lastFareUpdate()).isEqualTo(Optional.of(LocalDate.of(2026, 9, 28)));
    }
// --- signFile (SCRUM-184) -------------------------------------------------

    @Test
    void el_letrero_puede_ser_nulo_porque_tres_rutas_no_tienen_foto() {
        Route sinLetrero = new Route(1L, "Fusagasugá - Pasca", null, RouteType.INTERMUNICIPAL,
                RouteStatus.ACTIVA, List.of("Fusagasugá", "Pasca"), List.of(), List.of());

        assertThat(sinLetrero.signFile()).isNull();
    }

    @Test
    void el_letrero_con_formato_invalido_se_rechaza_en_el_constructor() {
        // Mismo formato que obliga ck_routes_sign_file en V14: si el dominio lo aceptara,
        // el error aparecería recién al escribir en la base.
        assertThatThrownBy(() -> new Route(1L, "Ruta", "centro-galeria", RouteType.URBANA,
                RouteStatus.ACTIVA, List.of("A"), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("centro-galeria");
    }

    @Test
    void el_letrero_en_mayusculas_con_guion_bajo_se_acepta() {
        Route conLetrero = new Route(1L, "Centro - Galería - Terminal", "CENTRO_GALERIA_TERMINAL",
                RouteType.URBANA, RouteStatus.ACTIVA, List.of("A", "B"), List.of(), List.of());

        assertThat(conLetrero.signFile()).isEqualTo("CENTRO_GALERIA_TERMINAL");
    }

    @Test
    void filtrar_las_tarifas_vigentes_conserva_el_letrero() {
        // withCurrentFares reconstruye el record: si se olvidara el campo, la ruta
        // llegaría al detalle sin letrero solo por haber pasado por ahí.
        Route route = route(RouteType.URBANA, RouteStatus.ACTIVA, List.of(fare(null, 2600, 0, "2026-02-05")));

        assertThat(route.withCurrentFares(TODAY).signFile()).isEqualTo(route.signFile());
    }

    // --- Coincidencia de lugares (SCRUM-184) ----------------------------------

    private static Route conLugares(String... lugares) {
        return new Route(1L, "Ruta", "RUTA", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of(lugares), List.of(), List.of());
    }

    @Test
    void la_coincidencia_ignora_mayusculas_tildes_y_puntos() {
        Route route = conLugares("U. Hospital Éxito", "Maíz Amarillo");

        assertThat(route.indexOfPlace("u hospital exito")).isEqualTo(OptionalInt.of(0));
        assertThat(route.indexOfPlace("MAIZ AMARILLO")).isEqualTo(OptionalInt.of(1));
    }

    @Test
    void la_coincidencia_exacta_le_gana_a_la_parcial_aunque_la_parcial_vaya_antes() {
        // Es el caso que hace útil la regla: varias rutas pasan por un centro comercial
        // antes que por el Centro. Sin la preferencia, buscar "Centro" daría el índice 0
        // y el punto de abordaje saldría en otro barrio.
        Route route = conLugares("Centro Comercial Manila", "Centro", "Terminal");

        assertThat(route.indexOfPlace("Centro")).isEqualTo(OptionalInt.of(1));
    }

    @Test
    void sin_coincidencia_exacta_se_acepta_que_el_termino_este_contenido() {
        Route route = conLugares("Antiguo Colsubsidio", "Terminal");

        assertThat(route.indexOfPlace("colsubsidio")).isEqualTo(OptionalInt.of(0));
    }

    @Test
    void el_origen_toma_la_primera_coincidencia_y_el_destino_la_ultima() {
        // Una ruta que vuelve a pasar por el sector de salida: con "primera para el
        // origen, última para el destino" sigue cumpliendo origen antes que destino.
        Route circular = conLugares("Centro", "Galería", "Terminal", "Galería", "Centro");

        assertThat(circular.indexOfPlace("Centro")).isEqualTo(OptionalInt.of(0));
        assertThat(circular.lastIndexOfPlace("Centro")).isEqualTo(OptionalInt.of(4));
        assertThat(circular.lastIndexOfPlace("Galería")).isEqualTo(OptionalInt.of(3));
    }

    @Test
    void un_termino_que_no_esta_en_la_ruta_no_coincide() {
        Route route = conLugares("Centro", "Terminal");

        assertThat(route.indexOfPlace("Chinauta")).isEmpty();
        assertThat(route.lastIndexOfPlace("Chinauta")).isEmpty();
    }

    @Test
    void un_termino_nulo_o_vacio_no_coincide_con_nada() {
        // Si la cadena vacía se tratara como contenida en todo, cualquier lugar
        // coincidiría y la búsqueda devolvería una ruta al azar.
        Route route = conLugares("Centro", "Terminal");

        assertThat(route.indexOfPlace(null)).isEmpty();
        assertThat(route.indexOfPlace("   ")).isEmpty();
    }

    @Test
    void el_punto_sobre_el_trazado_usa_la_misma_coincidencia_que_la_busqueda() {
        // Si aquí se comparara distinto, la ruta entraría como candidata y después se
        // quedaría sin punto de abordaje.
        Route route = new Route(1L, "Ruta", "RUTA", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("U. Hospital Éxito", "Terminal"), List.of(),
                List.of(new Coordinate(4.000, -74.000), new Coordinate(4.000, -74.010)));

        assertThat(route.pointAtNeighborhood("u hospital exito")).isPresent();
    }
}
