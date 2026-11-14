package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidSuspensionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas de la ventana de suspension (RF-12) con un reloj fijo: el limite de la ventana es semiabierto,
 * {@code [startsAt, endsAt)}.
 */
class RouteSuspensionTest {

    private static final Instant INICIO = Instant.parse("2026-11-20T13:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-22T13:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");

    private static RouteSuspension ventana() {
        return new RouteSuspension(1L, 12L, "Centro - Terminal", INICIO, FIN, null, null);
    }

    // --- vigencia ----------------------------------------------------------------------------

    @Test
    void justo_en_startsAt_ya_esta_vigente() {
        assertThat(ventana().isEffectiveAt(INICIO)).isTrue();
        assertThat(ventana().stateAt(INICIO)).isEqualTo(SuspensionState.ACTIVE);
    }

    @Test
    void un_instante_antes_de_startsAt_es_proxima_y_no_vigente() {
        Instant antes = INICIO.minusMillis(1);

        assertThat(ventana().isEffectiveAt(antes)).isFalse();
        assertThat(ventana().stateAt(antes)).isEqualTo(SuspensionState.UPCOMING);
    }

    @Test
    void justo_en_endsAt_ya_no_esta_vigente_y_la_ruta_vuelve() {
        assertThat(ventana().isEffectiveAt(FIN)).isFalse();
        assertThat(ventana().stateAt(FIN)).isEqualTo(SuspensionState.ENDED);
    }

    @Test
    void un_instante_antes_de_endsAt_sigue_vigente() {
        Instant casi = FIN.minusMillis(1);

        assertThat(ventana().isEffectiveAt(casi)).isTrue();
        assertThat(ventana().stateAt(casi)).isEqualTo(SuspensionState.ACTIVE);
    }

    @Test
    void levantada_nunca_esta_vigente_aunque_este_dentro_de_la_ventana() {
        RouteSuspension levantada = ventana().lifted(INICIO.plus(Duration.ofHours(5)));

        assertThat(levantada.isEffectiveAt(INICIO.plus(Duration.ofDays(1)))).isFalse();
        assertThat(levantada.stateAt(INICIO.plus(Duration.ofDays(1)))).isEqualTo(SuspensionState.LIFTED);
    }

    @Test
    void levantada_antes_de_empezar_es_LIFTED_y_no_UPCOMING() {
        RouteSuspension levantada = ventana().lifted(AHORA);

        assertThat(levantada.stateAt(AHORA)).isEqualTo(SuspensionState.LIFTED);
    }

    @Test
    void levantada_y_ya_pasado_el_fin_sigue_siendo_LIFTED() {
        RouteSuspension levantada = ventana().lifted(INICIO.plusSeconds(60));

        assertThat(levantada.stateAt(FIN.plus(Duration.ofDays(3)))).isEqualTo(SuspensionState.LIFTED);
    }

    // --- se puede levantar -------------------------------------------------------------------

    @Test
    void se_puede_levantar_una_proxima_y_una_vigente_pero_no_una_terminada_ni_una_levantada() {
        assertThat(ventana().canBeLiftedAt(AHORA)).isTrue();
        assertThat(ventana().canBeLiftedAt(INICIO.plusSeconds(1))).isTrue();
        assertThat(ventana().canBeLiftedAt(FIN)).isFalse();
        assertThat(ventana().lifted(INICIO).canBeLiftedAt(INICIO.plusSeconds(1))).isFalse();
    }

    // --- solape ------------------------------------------------------------------------------

    @Test
    void ventanas_que_se_cruzan_se_solapan() {
        assertThat(ventana().overlaps(INICIO.minus(Duration.ofDays(1)), INICIO.plusSeconds(1))).isTrue();
        assertThat(ventana().overlaps(FIN.minusSeconds(1), FIN.plus(Duration.ofDays(1)))).isTrue();
        assertThat(ventana().overlaps(INICIO.plusSeconds(60), INICIO.plusSeconds(120))).isTrue();
        assertThat(ventana().overlaps(INICIO.minus(Duration.ofDays(1)), FIN.plus(Duration.ofDays(1)))).isTrue();
    }

    @Test
    void ventanas_contiguas_no_se_solapan() {
        assertThat(ventana().overlaps(FIN, FIN.plus(Duration.ofDays(1)))).isFalse();
        assertThat(ventana().overlaps(INICIO.minus(Duration.ofDays(1)), INICIO)).isFalse();
    }

    @Test
    void ventanas_disjuntas_no_se_solapan() {
        assertThat(ventana().overlaps(FIN.plus(Duration.ofDays(1)), FIN.plus(Duration.ofDays(2)))).isFalse();
    }

    // --- construccion ------------------------------------------------------------------------

    @Test
    void el_constructor_rechaza_una_ventana_invertida_o_vacia() {
        assertThatThrownBy(() -> new RouteSuspension(null, 1L, "R", FIN, INICIO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RouteSuspension(null, 1L, "R", INICIO, INICIO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- validacion --------------------------------------------------------------------------

    @Test
    void open_con_datos_validos_crea_la_suspension_sin_id_ni_levantamiento() {
        RouteSuspension s = RouteSuspension.open(12L, "Centro - Terminal", INICIO, FIN, "  Obras en la Cra. 6  ", AHORA);

        assertThat(s.id()).isNull();
        assertThat(s.liftedAt()).isNull();
        assertThat(s.reason()).isEqualTo("Obras en la Cra. 6");
        assertThat(s.routeId()).isEqualTo(12L);
    }

    @Test
    void un_motivo_vacio_o_en_blanco_se_guarda_como_sin_motivo() {
        assertThat(RouteSuspension.open(1L, "R", INICIO, FIN, "   ", AHORA).reason()).isNull();
        assertThat(RouteSuspension.open(1L, "R", INICIO, FIN, null, AHORA).reason()).isNull();
    }

    @Test
    void el_fin_debe_ser_posterior_al_inicio() {
        assertThat(campos(INICIO, INICIO, null)).containsExactly("endsAt");
        assertThat(campos(FIN, INICIO, null)).containsExactly("endsAt");
    }

    @Test
    void el_fin_debe_estar_en_el_futuro() {
        Instant pasadoInicio = AHORA.minus(Duration.ofDays(3));
        Instant pasadoFin = AHORA.minus(Duration.ofDays(1));

        assertThat(campos(pasadoInicio, pasadoFin, null)).containsExactly("endsAt");
        // Justo "ahora" tampoco es futuro: la ventana ya estaria cerrada.
        assertThat(campos(pasadoInicio, AHORA, null)).containsExactly("endsAt");
    }

    @Test
    void el_inicio_puede_estar_en_el_pasado_si_el_fin_esta_en_el_futuro() {
        assertThat(campos(AHORA.minus(Duration.ofHours(2)), AHORA.plus(Duration.ofHours(2)), null)).isEmpty();
    }

    @Test
    void la_duracion_maxima_es_de_90_dias_y_90_justos_es_valido() {
        Instant inicio = AHORA.plusSeconds(60);

        assertThat(campos(inicio, inicio.plus(Duration.ofDays(90)), null)).isEmpty();
        assertThat(campos(inicio, inicio.plus(Duration.ofDays(90)).plusSeconds(1), null)).containsExactly("endsAt");
    }

    @Test
    void faltan_las_dos_fechas_y_se_reportan_juntas() {
        assertThat(campos(null, null, null)).containsExactly("startsAt", "endsAt");
    }

    @Test
    void falta_solo_una_fecha() {
        assertThat(campos(null, FIN, null)).containsExactly("startsAt");
        assertThat(campos(INICIO, null, null)).containsExactly("endsAt");
    }

    @Test
    void el_motivo_no_pasa_de_200_caracteres() {
        assertThat(campos(INICIO, FIN, "a".repeat(200))).isEmpty();
        assertThat(campos(INICIO, FIN, "a".repeat(201))).containsExactly("reason");
    }

    @Test
    void el_motivo_no_puede_llevar_enlaces() {
        assertThat(campos(INICIO, FIN, "Mas info en https://ejemplo.co/obras")).containsExactly("reason");
        assertThat(campos(INICIO, FIN, "Mas info en www.obras")).containsExactly("reason");
        assertThat(campos(INICIO, FIN, "Mas info en obras.com")).containsExactly("reason");
        assertThat(campos(INICIO, FIN, "Cierre por obras en la carrera 6")).isEmpty();
    }

    @Test
    void open_devuelve_todas_las_violaciones_juntas() {
        assertThatThrownBy(() -> RouteSuspension.open(1L, "R", FIN, INICIO, "ver www.x", AHORA))
                .isInstanceOfSatisfying(InvalidSuspensionException.class, e ->
                        assertThat(e.getViolations()).extracting(FieldViolation::field)
                                .containsExactly("endsAt", "reason"));
    }

    private static List<String> campos(Instant inicio, Instant fin, String motivo) {
        return RouteSuspension.violations(inicio, fin, motivo, AHORA).stream().map(FieldViolation::field).toList();
    }

    // --- filtro del listado ------------------------------------------------------------------

    @Test
    void el_filtro_past_agrupa_terminadas_y_levantadas() {
        assertThat(SuspensionFilter.PAST.accepts(SuspensionState.ENDED)).isTrue();
        assertThat(SuspensionFilter.PAST.accepts(SuspensionState.LIFTED)).isTrue();
        assertThat(SuspensionFilter.PAST.accepts(SuspensionState.ACTIVE)).isFalse();
        assertThat(SuspensionFilter.ACTIVE.accepts(SuspensionState.UPCOMING)).isFalse();
        assertThat(SuspensionFilter.UPCOMING.accepts(SuspensionState.UPCOMING)).isTrue();
        assertThat(SuspensionFilter.ALL.accepts(SuspensionState.LIFTED)).isTrue();
    }

    @Test
    void el_filtro_sin_valor_es_all_ignora_mayusculas_y_rechaza_lo_desconocido() {
        assertThat(SuspensionFilter.parse(null)).isEqualTo(SuspensionFilter.ALL);
        assertThat(SuspensionFilter.parse(" ")).isEqualTo(SuspensionFilter.ALL);
        assertThat(SuspensionFilter.parse("Active")).isEqualTo(SuspensionFilter.ACTIVE);
        assertThatThrownBy(() -> SuspensionFilter.parse("ayer"))
                .isInstanceOfSatisfying(InvalidSuspensionException.class, e ->
                        assertThat(e.getViolations()).extracting(FieldViolation::field).containsExactly("state"));
    }
}
