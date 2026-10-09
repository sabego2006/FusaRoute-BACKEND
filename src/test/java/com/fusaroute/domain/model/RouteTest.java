package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    private static Fare fare(String point, int amount, int order, String validFrom) {
        return new Fare(point, BigDecimal.valueOf(amount), order, LocalDate.parse(validFrom));
    }

    private static Route route(RouteType type, RouteStatus status, List<Fare> fares) {
        return new Route(1L, "Ruta", type, status, List.of("A", "B"), fares, List.of());
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
}
