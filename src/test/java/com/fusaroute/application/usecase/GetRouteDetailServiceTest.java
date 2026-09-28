package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
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
class GetRouteDetailServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private RouteRepositoryPort routeRepository;

    private static Route route(RouteStatus status) {
        return new Route(7L, "Fusagasugá - Pasca", RouteType.INTERMUNICIPAL, status,
                List.of("Fusagasugá", "Alaska", "Pasca"),
                List.of(new Fare("Alaska", BigDecimal.valueOf(3300), 3, LocalDate.of(2025, 1, 16)),
                        new Fare("Alaska", BigDecimal.valueOf(3550), 0, LocalDate.of(2026, 2, 5))));
    }

    private GetRouteDetailService service() {
        return new GetRouteDetailService(routeRepository, CLOCK);
    }

    @Test
    void ruta_activa_se_devuelve_con_la_tarifa_vigente() {
        when(routeRepository.findById(7L)).thenReturn(Optional.of(route(RouteStatus.ACTIVA)));

        Route result = service().getById(7L);

        assertThat(result.name()).isEqualTo("Fusagasugá - Pasca");
        assertThat(result.neighborhoods()).containsExactly("Fusagasugá", "Alaska", "Pasca");
        assertThat(result.fares()).hasSize(1);
        assertThat(result.fares().get(0).amount()).isEqualByComparingTo("3550");
    }

    @Test
    void ruta_inexistente_lanza_not_found() {
        when(routeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(99L)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void ruta_suspendida_se_trata_como_inexistente() {
        when(routeRepository.findById(7L)).thenReturn(Optional.of(route(RouteStatus.SUSPENDIDA)));

        assertThatThrownBy(() -> service().getById(7L)).isInstanceOf(RouteNotFoundException.class);
    }
}
