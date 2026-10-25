package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.in.GetRouteFareUseCase.RouteFare;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRouteFareServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private RouteRepositoryPort routeRepository;

    private static Route urbana(RouteStatus status, List<Fare> fares) {
        return new Route(1L, "Centro - Terminal", RouteType.URBANA, status, List.of("Centro", "Terminal"), fares,
                List.of());
    }

    private static Route intermunicipal(RouteStatus status, List<Fare> fares) {
        return new Route(7L, "Fusagasugá - Pasca", RouteType.INTERMUNICIPAL, status,
                List.of("Fusagasugá", "Alaska", "Pasca"), fares, List.of());
    }

    private GetRouteFareService service() {
        return new GetRouteFareService(routeRepository, CLOCK);
    }

    @Test
    void ruta_urbana_activa_devuelve_una_sola_tarifa_con_punto_null() {
        Fare fare = new Fare(null, BigDecimal.valueOf(2800), 0, LocalDate.of(2026, 9, 27));
        when(routeRepository.findById(1L)).thenReturn(Optional.of(urbana(RouteStatus.ACTIVA, List.of(fare))));

        RouteFare result = service().getByRouteId(1L);

        assertThat(result.fares()).hasSize(1);
        assertThat(result.fares().get(0).referencePoint()).isNull();
        assertThat(result.fares().get(0).amount()).isEqualByComparingTo("2800");
        assertThat(result.lastUpdatedAt()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    @Test
    void ruta_intermunicipal_activa_devuelve_tarifas_ordenadas_de_menor_a_mayor() {
        List<Fare> fares = List.of(
                new Fare("Pasca", BigDecimal.valueOf(4300), 7, LocalDate.of(2025, 1, 16)),
                new Fare("Alaska", BigDecimal.valueOf(3550), 0, LocalDate.of(2026, 2, 5)));
        when(routeRepository.findById(7L)).thenReturn(Optional.of(intermunicipal(RouteStatus.ACTIVA, fares)));

        RouteFare result = service().getByRouteId(7L);

        assertThat(result.fares()).extracting(f -> f.amount().intValue()).containsExactly(3550, 4300);
        assertThat(result.lastUpdatedAt()).isEqualTo(LocalDate.of(2026, 2, 5));
    }

    @Test
    void tarifa_futura_queda_excluida_y_no_cuenta_para_la_ultima_actualizacion() {
        List<Fare> fares = List.of(
                new Fare(null, BigDecimal.valueOf(2600), 0, LocalDate.of(2026, 2, 5)),
                new Fare(null, BigDecimal.valueOf(2800), 0, LocalDate.of(2026, 9, 28)));
        when(routeRepository.findById(1L)).thenReturn(Optional.of(urbana(RouteStatus.ACTIVA, fares)));

        RouteFare result = service().getByRouteId(1L);

        assertThat(result.fares()).hasSize(1);
        assertThat(result.fares().get(0).amount()).isEqualByComparingTo("2600");
        assertThat(result.lastUpdatedAt()).isEqualTo(LocalDate.of(2026, 2, 5));
    }

    @Test
    void series_de_vigencia_solapadas_eligen_la_fila_mas_reciente_por_punto() {
        List<Fare> fares = List.of(
                new Fare("Alaska", BigDecimal.valueOf(3300), 3, LocalDate.of(2025, 1, 16)),
                new Fare("Alaska", BigDecimal.valueOf(3550), 0, LocalDate.of(2026, 2, 5)));
        when(routeRepository.findById(7L)).thenReturn(Optional.of(intermunicipal(RouteStatus.ACTIVA, fares)));

        RouteFare result = service().getByRouteId(7L);

        assertThat(result.fares()).hasSize(1);
        assertThat(result.fares().get(0).amount()).isEqualByComparingTo("3550");
    }

    @Test
    void ruta_suspendida_se_trata_como_inexistente() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(urbana(RouteStatus.SUSPENDIDA, List.of())));

        assertThatThrownBy(() -> service().getByRouteId(1L)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void ruta_inexistente_lanza_not_found() {
        when(routeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getByRouteId(99L)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void ruta_activa_sin_tarifas_vigentes_devuelve_lista_vacia_y_sin_ultima_actualizacion() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(urbana(RouteStatus.ACTIVA, List.of())));

        RouteFare result = service().getByRouteId(1L);

        assertThat(result.fares()).isEmpty();
        assertThat(result.lastUpdatedAt()).isNull();
    }
}
