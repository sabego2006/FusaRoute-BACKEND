package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.AdminMetrics;
import com.fusaroute.domain.model.RouteSearchCount;
import com.fusaroute.domain.model.TopRoute;
import com.fusaroute.domain.port.in.CountPendingFeedbackUseCase;
import com.fusaroute.domain.port.out.EffectiveSuspensionCountPort;
import com.fusaroute.domain.port.out.RouteNamePort;
import com.fusaroute.domain.port.out.UsageStatsPort;
import com.fusaroute.domain.port.out.UserCountPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Panel de metricas de RF-13 (SCRUM-26) con los puertos mockeados y un reloj fijo: ningun test depende de la
 * hora real ni de la zona del equipo que lo corre.
 */
@ExtendWith(MockitoExtension.class)
class GetAdminMetricsServiceTest {

    /** 2026-11-13 10:15:30 en Bogota (-05:00). */
    private static final Instant AHORA = Instant.parse("2026-11-13T15:15:30Z");

    @Mock
    private UserCountPort users;
    @Mock
    private UsageStatsPort usage;
    @Mock
    private CountPendingFeedbackUseCase pendingFeedback;
    @Mock
    private EffectiveSuspensionCountPort suspensions;
    @Mock
    private RouteNamePort routeNames;

    private GetAdminMetricsService serviceAt(Instant now) {
        return new GetAdminMetricsService(users, usage, pendingFeedback, suspensions, routeNames,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @BeforeEach
    void rutasConNombre() {
        // Tolerante: solo lo usan los tests que arman un ranking.
        org.mockito.Mockito.lenient().when(routeNames.findNamesByIds(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            Map<Long, String> nombres = new HashMap<>();
            // ids es null cuando otro test re-declara este mismo stub con any(): Mockito invoca la respuesta una vez.
            if (ids == null) {
                return nombres;
            }
            ids.forEach(id -> nombres.put(id, "Ruta " + id));
            return nombres;
        });
    }

    private static RouteSearchCount conteo(long ruta, long busquedas) {
        return new RouteSearchCount(ruta, busquedas);
    }

    // --- las cinco metricas y los metadatos ----------------------------------------------------

    @Test
    void devuelve_las_cinco_metricas_y_los_metadatos() {
        when(users.countAll()).thenReturn(120L);
        when(usage.countSearchesBetween(any(), any())).thenReturn(37L);
        when(usage.countSearchesByRoute(any())).thenReturn(List.of(conteo(3, 9)));
        when(pendingFeedback.countPending()).thenReturn(4L);
        when(suspensions.countEffectiveAt(AHORA)).thenReturn(2L);

        AdminMetrics metricas = serviceAt(AHORA).getMetrics();

        assertThat(metricas.totalUsers()).isEqualTo(120);
        assertThat(metricas.searchesToday()).isEqualTo(37);
        assertThat(metricas.topRoutes()).containsExactly(new TopRoute(3L, "Ruta 3", 9));
        assertThat(metricas.pendingComments()).isEqualTo(4);
        assertThat(metricas.activeSuspensions()).isEqualTo(2);
        assertThat(metricas.timezone()).isEqualTo("America/Bogota");
        assertThat(metricas.topRoutesWindowDays()).isEqualTo(30);
        // generatedAt es el mismo instante, con el offset de Colombia.
        assertThat(metricas.generatedAt()).isEqualTo(OffsetDateTime.parse("2026-11-13T10:15:30-05:00"));
        assertThat(metricas.generatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    }

    @Test
    void las_busquedas_de_hoy_van_de_las_00_00_de_bogota_a_las_00_00_del_dia_siguiente() {
        serviceAt(AHORA).getMetrics();

        verify(usage).countSearchesBetween(Instant.parse("2026-11-13T05:00:00Z"), Instant.parse("2026-11-14T05:00:00Z"));
    }

    @Test
    void pasadas_las_19_00_en_colombia_el_dia_sigue_siendo_el_de_colombia_no_el_de_utc() {
        // 2026-11-14T02:00Z ya es 14 en UTC, pero en Bogota son las 21:00 del 13.
        serviceAt(Instant.parse("2026-11-14T02:00:00Z")).getMetrics();

        verify(usage).countSearchesBetween(Instant.parse("2026-11-13T05:00:00Z"), Instant.parse("2026-11-14T05:00:00Z"));
    }

    // --- borde de medianoche ------------------------------------------------------------------

    /** Un puerto de verdad, en memoria, que aplica el rango semiabierto: asi se prueba el borde y no solo la llamada. */
    private static UsageStatsPort eventosEn(Instant... momentos) {
        return new UsageStatsPort() {
            @Override
            public long countSearchesBetween(Instant fromInclusive, Instant toExclusive) {
                return List.of(momentos).stream()
                        .filter(t -> !t.isBefore(fromInclusive) && t.isBefore(toExclusive)).count();
            }

            @Override
            public List<RouteSearchCount> countSearchesByRoute(Instant since) {
                return List.of();
            }
        };
    }

    @Test
    void un_evento_a_las_23_59_de_colombia_cuenta_para_ese_dia_y_uno_a_las_00_00_del_siguiente_no() {
        Instant a2359 = Instant.parse("2026-11-13T04:59:00Z");   // 2026-11-12 23:59 -05:00
        Instant a0000 = Instant.parse("2026-11-13T05:00:00Z");   // 2026-11-13 00:00 -05:00
        UsageStatsPort eventos = eventosEn(a2359, a0000);

        // Consultado a las 23:59:30 del 12: cuenta el de las 23:59, no el de las 00:00 del 13.
        AdminMetrics antesDeMedianoche = new GetAdminMetricsService(users, eventos, pendingFeedback, suspensions,
                routeNames, Clock.fixed(Instant.parse("2026-11-13T04:59:30Z"), ZoneOffset.UTC)).getMetrics();
        // Consultado a las 00:00:10 del 13: el dia cambio; el de las 23:59 ya es de ayer y el de las 00:00 cuenta.
        AdminMetrics despuesDeMedianoche = new GetAdminMetricsService(users, eventos, pendingFeedback, suspensions,
                routeNames, Clock.fixed(Instant.parse("2026-11-13T05:00:10Z"), ZoneOffset.UTC)).getMetrics();

        assertThat(antesDeMedianoche.searchesToday()).isEqualTo(1);
        assertThat(despuesDeMedianoche.searchesToday()).isEqualTo(1);
        assertThat(antesDeMedianoche.generatedAt().toLocalDate().toString()).isEqualTo("2026-11-12");
        assertThat(despuesDeMedianoche.generatedAt().toLocalDate().toString()).isEqualTo("2026-11-13");
    }

    @Test
    void el_dia_de_hoy_incluye_exactamente_las_00_00_y_excluye_las_24_00() {
        Instant inicio = Instant.parse("2026-11-13T05:00:00Z");
        Instant ultimoMilisegundo = Instant.parse("2026-11-14T04:59:59.999Z");
        Instant finExclusivo = Instant.parse("2026-11-14T05:00:00Z");
        UsageStatsPort eventos = eventosEn(inicio.minusMillis(1), inicio, ultimoMilisegundo, finExclusivo);

        AdminMetrics metricas = new GetAdminMetricsService(users, eventos, pendingFeedback, suspensions, routeNames,
                Clock.fixed(AHORA, ZoneOffset.UTC)).getMetrics();

        assertThat(metricas.searchesToday()).isEqualTo(2);
    }

    // --- ranking de rutas ---------------------------------------------------------------------

    @Test
    void el_ranking_mira_los_ultimos_30_dias() {
        serviceAt(AHORA).getMetrics();

        verify(usage).countSearchesByRoute(Instant.parse("2026-10-14T15:15:30Z"));
    }

    @Test
    void el_ranking_es_de_maximo_5_rutas_por_busquedas_descendente() {
        when(usage.countSearchesByRoute(any())).thenReturn(List.of(
                conteo(1, 4), conteo(2, 40), conteo(3, 12), conteo(4, 7), conteo(5, 99), conteo(6, 1), conteo(7, 25)));

        List<TopRoute> ranking = serviceAt(AHORA).getMetrics().topRoutes();

        assertThat(ranking).extracting(TopRoute::routeId).containsExactly(5L, 2L, 7L, 3L, 4L);
        assertThat(ranking).extracting(TopRoute::searches).containsExactly(99L, 40L, 25L, 12L, 7L);
    }

    @Test
    void a_igual_numero_de_busquedas_desempata_por_nombre() {
        when(routeNames.findNamesByIds(any())).thenReturn(Map.of(
                1L, "Terminal - Centro", 2L, "Arbeláez", 3L, "centro - Galería", 4L, "Chinauta"));
        when(usage.countSearchesByRoute(any())).thenReturn(List.of(
                conteo(1, 10), conteo(2, 10), conteo(3, 10), conteo(4, 11)));

        List<TopRoute> ranking = serviceAt(AHORA).getMetrics().topRoutes();

        // 4 gana por numero; los tres empatados salen por nombre sin distinguir mayusculas.
        assertThat(ranking).extracting(TopRoute::routeName)
                .containsExactly("Chinauta", "Arbeláez", "centro - Galería", "Terminal - Centro");
    }

    @Test
    void un_empate_total_se_resuelve_por_id_para_que_el_orden_sea_estable() {
        when(routeNames.findNamesByIds(any())).thenReturn(Map.of(9L, "Igual", 4L, "Igual"));
        when(usage.countSearchesByRoute(any())).thenReturn(List.of(conteo(9, 3), conteo(4, 3)));

        assertThat(serviceAt(AHORA).getMetrics().topRoutes()).extracting(TopRoute::routeId).containsExactly(4L, 9L);
    }

    @Test
    void sin_busquedas_con_ruta_el_ranking_es_vacio_y_no_se_piden_nombres() {
        when(usage.countSearchesByRoute(any())).thenReturn(List.of());

        assertThat(serviceAt(AHORA).getMetrics().topRoutes()).isEmpty();

        verifyNoInteractions(routeNames);
    }

    @Test
    void una_ruta_que_ya_no_existe_se_omite_y_el_ranking_se_completa_con_la_siguiente() {
        // La ruta 5 tiene mas busquedas pero ya no esta en routes: no sale, y entra la sexta.
        when(routeNames.findNamesByIds(any())).thenReturn(Map.of(
                1L, "A", 2L, "B", 3L, "C", 4L, "D", 6L, "F", 7L, "G"));
        when(usage.countSearchesByRoute(any())).thenReturn(List.of(
                conteo(5, 500), conteo(1, 60), conteo(2, 50), conteo(3, 40), conteo(4, 30), conteo(6, 20), conteo(7, 10)));

        List<TopRoute> ranking = serviceAt(AHORA).getMetrics().topRoutes();

        assertThat(ranking).extracting(TopRoute::routeId).containsExactly(1L, 2L, 3L, 4L, 6L);
        assertThat(ranking).extracting(TopRoute::routeId).doesNotContain(5L);
    }

    @Test
    void una_ruta_inactiva_o_suspendida_que_el_puerto_nombra_se_muestra_igual() {
        // El puerto de nombres devuelve todas las rutas existentes sin importar su estado: el ranking es historico.
        when(routeNames.findNamesByIds(any())).thenReturn(Map.of(8L, "Ruta dada de baja"));
        when(usage.countSearchesByRoute(any())).thenReturn(List.of(conteo(8, 5)));

        assertThat(serviceAt(AHORA).getMetrics().topRoutes())
                .containsExactly(new TopRoute(8L, "Ruta dada de baja", 5));
    }

    @Test
    void todas_las_rutas_del_ranking_inexistentes_dejan_el_ranking_vacio() {
        when(routeNames.findNamesByIds(any())).thenReturn(Map.of());
        when(usage.countSearchesByRoute(any())).thenReturn(List.of(conteo(1, 5), conteo(2, 3)));

        assertThat(serviceAt(AHORA).getMetrics().topRoutes()).isEmpty();
    }

    @Test
    void el_ranking_devuelto_no_se_puede_modificar() {
        when(usage.countSearchesByRoute(any())).thenReturn(List.of(conteo(1, 5)));

        List<TopRoute> ranking = serviceAt(AHORA).getMetrics().topRoutes();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ranking.add(new TopRoute(2L, "X", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- sin cache ----------------------------------------------------------------------------

    @Test
    void cada_llamada_recalcula_sin_cache() {
        when(users.countAll()).thenReturn(10L, 11L);

        GetAdminMetricsService servicio = serviceAt(AHORA);

        assertThat(servicio.getMetrics().totalUsers()).isEqualTo(10);
        assertThat(servicio.getMetrics().totalUsers()).isEqualTo(11);
    }
}
