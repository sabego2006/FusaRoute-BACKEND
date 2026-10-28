package com.fusaroute.application.usecase;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListActiveRoutesServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), BOGOTA);

    @Mock
    private RouteRepositoryPort routeRepository;

    private static Route route(long id, String name, RouteStatus status, Fare... fares) {
        return new Route(id, name, RouteType.URBANA, status, List.of("Centro"), List.of(fares), List.of());
    }

    private static Fare fare(int amount, String validFrom) {
        return new Fare(null, BigDecimal.valueOf(amount), 0, LocalDate.parse(validFrom));
    }

    private ListActiveRoutesService service() {
        return new ListActiveRoutesService(routeRepository, CLOCK);
    }

    @Test
    void excluye_las_rutas_suspendidas() {
        when(routeRepository.findAll()).thenReturn(List.of(
                route(1, "Activa", RouteStatus.ACTIVA, fare(2600, "2026-02-05")),
                route(2, "Suspendida", RouteStatus.SUSPENDIDA, fare(2600, "2026-02-05"))));

        assertThat(service().listActive()).extracting(Route::name).containsExactly("Activa");
    }

    @Test
    void ordena_por_nombre_con_la_tilde_junto_a_su_letra() {
        when(routeRepository.findAll()).thenReturn(List.of(
                route(1, "Pablo Bello", RouteStatus.ACTIVA),
                route(2, "Maíz Amarillo", RouteStatus.ACTIVA),
                route(3, "Camino Real", RouteStatus.ACTIVA),
                route(4, "Ábaco", RouteStatus.ACTIVA)));

        assertThat(service().listActive()).extracting(Route::name)
                .containsExactly("Ábaco", "Camino Real", "Maíz Amarillo", "Pablo Bello");
    }

    @Test
    void reduce_las_tarifas_a_la_vigente_y_descarta_la_futura() {
        when(routeRepository.findAll()).thenReturn(List.of(
                route(1, "Ruta", RouteStatus.ACTIVA,
                        fare(2500, "2025-01-01"), fare(2600, "2026-02-05"), fare(2900, "2026-10-01"))));

        List<Fare> fares = service().listActive().get(0).fares();

        assertThat(fares).hasSize(1);
        assertThat(fares.get(0).amount()).isEqualByComparingTo("2600");
    }

    @Test
    void sin_rutas_devuelve_lista_vacia() {
        when(routeRepository.findAll()).thenReturn(List.of());

        assertThat(service().listActive()).isEmpty();
    }
}
