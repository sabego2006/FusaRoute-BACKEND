package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDraftTest {

    private static final List<List<Double>> TRAZADO = List.of(List.of(-74.37, 4.34), List.of(-74.36, 4.35));

    private static FareDraft tarifa(String monto, String punto) {
        return new FareDraft(new BigDecimal(monto), punto);
    }

    private static RouteDraft urbana() {
        return new RouteDraft("Centro - Terminal", RouteType.URBANA, "LineString", TRAZADO,
                List.of("Centro", "Terminal"), List.of(tarifa("2600", null)), "CENTRO_TERMINAL");
    }

    private static RouteDraft conNombre(String nombre) {
        RouteDraft d = urbana();
        return new RouteDraft(nombre, d.type(), d.pathType(), d.pathPositions(), d.neighborhoods(), d.fares(), d.signFile());
    }

    private static RouteDraft conBarrios(List<String> barrios) {
        RouteDraft d = urbana();
        return new RouteDraft(d.name(), d.type(), d.pathType(), d.pathPositions(), barrios, d.fares(), d.signFile());
    }

    private static RouteDraft conTrazado(String tipo, List<List<Double>> posiciones) {
        RouteDraft d = urbana();
        return new RouteDraft(d.name(), d.type(), tipo, posiciones, d.neighborhoods(), d.fares(), d.signFile());
    }

    private static RouteDraft conTarifas(RouteType tipo, List<FareDraft> tarifas) {
        RouteDraft d = urbana();
        return new RouteDraft(d.name(), tipo, d.pathType(), d.pathPositions(), d.neighborhoods(), tarifas, d.signFile());
    }

    private static List<String> campos(RouteDraft draft) {
        return draft.violations().stream().map(FieldViolation::field).toList();
    }

    @Test
    void una_ruta_urbana_valida_no_tiene_incumplimientos() {
        assertThat(urbana().violations()).isEmpty();
    }

    @Test
    void el_trazado_y_el_letrero_son_opcionales() {
        RouteDraft d = urbana();
        RouteDraft sin = new RouteDraft(d.name(), d.type(), null, null, d.neighborhoods(), d.fares(), null);

        assertThat(sin.violations()).isEmpty();
        assertThat(sin.hasPath()).isFalse();
    }

    @Test
    void el_nombre_es_obligatorio() {
        assertThat(campos(conNombre(null))).containsExactly("name");
        assertThat(campos(conNombre("   "))).containsExactly("name");
    }

    @Test
    void el_nombre_va_de_3_a_140_caracteres() {
        assertThat(campos(conNombre("ab"))).containsExactly("name");
        assertThat(conNombre("abc").violations()).isEmpty();
        assertThat(conNombre("a".repeat(140)).violations()).isEmpty();
        assertThat(campos(conNombre("a".repeat(141)))).containsExactly("name");
    }

    @Test
    void el_nombre_no_admite_espacios_sobrantes() {
        assertThat(campos(conNombre(" Centro - Terminal"))).containsExactly("name");
        assertThat(campos(conNombre("Centro - Terminal "))).containsExactly("name");
        assertThat(campos(conNombre("Centro  Terminal"))).containsExactly("name");
    }

    @Test
    void el_tipo_es_obligatorio() {
        RouteDraft d = urbana();
        RouteDraft sinTipo = new RouteDraft(d.name(), null, d.pathType(), d.pathPositions(),
                d.neighborhoods(), d.fares(), d.signFile());

        assertThat(campos(sinTipo)).containsExactly("type");
    }

    @Test
    void el_trazado_debe_ser_un_linestring() {
        assertThat(campos(conTrazado("Polygon", TRAZADO))).containsExactly("pathGeoJson");
        assertThat(campos(conTrazado(null, TRAZADO))).containsExactly("pathGeoJson");
    }

    @Test
    void el_trazado_necesita_al_menos_dos_posiciones() {
        assertThat(campos(conTrazado("LineString", List.of(List.of(-74.37, 4.34))))).containsExactly("pathGeoJson");
        assertThat(campos(conTrazado("LineString", null))).containsExactly("pathGeoJson");
    }

    @Test
    void el_trazado_valida_el_rango_de_longitud_y_latitud() {
        assertThat(campos(conTrazado("LineString", List.of(List.of(-181.0, 4.34), List.of(-74.0, 4.35)))))
                .containsExactly("pathGeoJson");
        assertThat(campos(conTrazado("LineString", List.of(List.of(-74.0, 4.34), List.of(-74.0, 90.1)))))
                .containsExactly("pathGeoJson");
        assertThat(conTrazado("LineString", List.of(List.of(-180.0, -90.0), List.of(180.0, 90.0))).violations())
                .isEmpty();
    }

    @Test
    void cada_posicion_del_trazado_necesita_longitud_y_latitud() {
        assertThat(campos(conTrazado("LineString", List.of(List.of(-74.0), List.of(-74.0, 4.35)))))
                .containsExactly("pathGeoJson");
        List<List<Double>> conNulo = new ArrayList<>(TRAZADO);
        conNulo.add(null);
        assertThat(campos(conTrazado("LineString", conNulo))).containsExactly("pathGeoJson");
    }

    @Test
    void los_barrios_van_de_1_a_40() {
        assertThat(campos(conBarrios(List.of()))).containsExactly("neighborhoods");
        assertThat(campos(conBarrios(null))).containsExactly("neighborhoods");
        List<String> cuarenta = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            cuarenta.add("Barrio " + i);
        }
        assertThat(conBarrios(cuarenta).violations()).isEmpty();
        cuarenta.add("Barrio 40");
        assertThat(campos(conBarrios(cuarenta))).containsExactly("neighborhoods");
    }

    @Test
    void los_barrios_no_se_repiten_ni_van_vacios_ni_pasan_de_120() {
        assertThat(campos(conBarrios(List.of("Centro", "centro")))).containsExactly("neighborhoods");
        assertThat(campos(conBarrios(List.of("Centro", " ")))).containsExactly("neighborhoods");
        assertThat(campos(conBarrios(List.of("a".repeat(121))))).containsExactly("neighborhoods");
        assertThat(conBarrios(List.of("a".repeat(120))).violations()).isEmpty();
    }

    @Test
    void la_urbana_lleva_exactamente_una_tarifa_sin_punto() {
        assertThat(campos(conTarifas(RouteType.URBANA, List.of(tarifa("2600", null), tarifa("3000", null)))))
                .containsExactly("fares");
        assertThat(campos(conTarifas(RouteType.URBANA, List.of(tarifa("2600", "Alaska"))))).containsExactly("fares");
        assertThat(campos(conTarifas(RouteType.URBANA, List.of()))).containsExactly("fares");
    }

    @Test
    void la_intermunicipal_lleva_una_o_mas_tarifas_con_punto_distinto() {
        assertThat(conTarifas(RouteType.INTERMUNICIPAL, List.of(tarifa("3000", "Alaska"))).violations()).isEmpty();
        assertThat(conTarifas(RouteType.INTERMUNICIPAL,
                List.of(tarifa("4000", "Pasca"), tarifa("3000", "Alaska"))).violations()).isEmpty();
        assertThat(campos(conTarifas(RouteType.INTERMUNICIPAL, List.of(tarifa("3000", null)))))
                .containsExactly("fares");
        assertThat(campos(conTarifas(RouteType.INTERMUNICIPAL, List.of(tarifa("3000", " ")))))
                .containsExactly("fares");
        assertThat(campos(conTarifas(RouteType.INTERMUNICIPAL,
                List.of(tarifa("3000", "Alaska"), tarifa("3500", "alaska"))))).containsExactly("fares");
    }

    @Test
    void el_monto_debe_ser_mayor_que_cero_y_caber_en_la_columna() {
        assertThat(campos(conTarifas(RouteType.URBANA, List.of(tarifa("0", null))))).containsExactly("fares");
        assertThat(campos(conTarifas(RouteType.URBANA, List.of(tarifa("-100", null))))).containsExactly("fares");
        assertThat(campos(conTarifas(RouteType.URBANA, List.of(new FareDraft(null, null))))).containsExactly("fares");
        assertThat(campos(conTarifas(RouteType.URBANA, List.of(tarifa("2600.555", null))))).containsExactly("fares");
        assertThat(campos(conTarifas(RouteType.URBANA, List.of(tarifa("100000000", null))))).containsExactly("fares");
        assertThat(conTarifas(RouteType.URBANA, List.of(tarifa("2600.50", null))).violations()).isEmpty();
    }

    @Test
    void el_letrero_respeta_el_formato_de_la_base() {
        RouteDraft d = urbana();
        RouteDraft minusculas = new RouteDraft(d.name(), d.type(), d.pathType(), d.pathPositions(),
                d.neighborhoods(), d.fares(), "centro-terminal");

        assertThat(campos(minusculas)).containsExactly("signFile");
    }

    @Test
    void acumula_todos_los_incumplimientos_juntos() {
        RouteDraft malo = new RouteDraft("a", null, "Point", null, List.of(), List.of(), "x y");

        assertThat(campos(malo)).containsExactlyInAnyOrder("name", "type", "pathGeoJson", "neighborhoods", "fares",
                "signFile");
    }
}
