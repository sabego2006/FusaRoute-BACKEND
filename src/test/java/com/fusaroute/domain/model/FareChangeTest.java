package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FareChangeTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);
    private static final LocalDate ANTES = LocalDate.of(2026, 2, 5);

    private static Fare vigente(String punto, String monto, int indice) {
        return new Fare(punto, new BigDecimal(monto), indice, ANTES);
    }

    private static FareDraft pedida(String monto, String punto) {
        return new FareDraft(new BigDecimal(monto), punto);
    }

    @Test
    void sin_cambios_no_se_toca_nada_aunque_el_monto_traiga_otra_escala() {
        FareChange change = FareChange.between(List.of(vigente(null, "2600.00", 0)),
                List.of(pedida("2600", null)), HOY);

        assertThat(change.changed()).isFalse();
        assertThat(change.toWrite()).isEmpty();
        assertThat(change.removedPoints()).isEmpty();
    }

    @Test
    void un_monto_distinto_genera_una_fila_nueva_con_vigencia_de_hoy() {
        FareChange change = FareChange.between(List.of(vigente(null, "2600", 0)), List.of(pedida("2800", null)), HOY);

        assertThat(change.changed()).isTrue();
        assertThat(change.toWrite()).containsExactly(new Fare(null, new BigDecimal("2800"), 0, HOY));
        assertThat(change.removedPoints()).isEmpty();
    }

    @Test
    void las_filas_se_ordenan_por_monto_ascendente_para_el_order_index() {
        FareChange change = FareChange.between(List.of(vigente("Alaska", "3000", 0)),
                List.of(pedida("5000", "Pasca"), pedida("3000", "Alaska"), pedida("4000", "Retorno")), HOY);

        List<String> puntos = new ArrayList<>();
        change.toWrite().forEach(f -> puntos.add(f.orderIndex() + ":" + f.referencePoint()));
        assertThat(puntos).containsExactly("0:Alaska", "1:Retorno", "2:Pasca");
        assertThat(change.toWrite()).allMatch(f -> f.validFrom().equals(HOY));
    }

    @Test
    void un_punto_nuevo_cuenta_como_cambio() {
        FareChange change = FareChange.between(List.of(vigente("Alaska", "3000", 0)),
                List.of(pedida("3000", "Alaska"), pedida("4000", "Pasca")), HOY);

        assertThat(change.changed()).isTrue();
    }

    @Test
    void un_punto_que_desaparece_se_reporta_para_eliminar_sus_filas() {
        FareChange change = FareChange.between(
                List.of(vigente("Alaska", "3000", 0), vigente("Pasca", "4000", 1)),
                List.of(pedida("3000", "Alaska")), HOY);

        assertThat(change.changed()).isTrue();
        assertThat(change.removedPoints()).containsExactly("Pasca");
    }

    @Test
    void pasar_de_urbana_a_intermunicipal_elimina_la_tarifa_sin_punto() {
        FareChange change = FareChange.between(List.of(vigente(null, "2600", 0)),
                List.of(pedida("3000", "Alaska")), HOY);

        assertThat(change.removedPoints()).containsExactly((String) null);
    }

    @Test
    void ignora_espacios_alrededor_del_punto_de_referencia() {
        FareChange change = FareChange.between(List.of(vigente("Alaska", "3000", 0)),
                List.of(pedida("3000", " Alaska ")), HOY);

        assertThat(change.changed()).isFalse();
    }
}
