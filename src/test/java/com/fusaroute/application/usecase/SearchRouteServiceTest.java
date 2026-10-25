package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.TravelTimePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchRouteServiceTest {

    private static final Coordinate ORIGIN = new Coordinate(4.3433, -74.3661);
    private static final Coordinate DESTINATION = new Coordinate(4.3300, -74.3600);

    @Mock
    private RouteRepositoryPort routeRepository;

    @Mock
    private TravelTimePort travelTimePort;

    private SearchRouteService service() {
        return new SearchRouteService(routeRepository, travelTimePort, new SimpleMeterRegistry());
    }

    private static Route route(long id, String name, RouteStatus status, String... neighborhoods) {
        return new Route(id, name, RouteType.URBANA, status, List.of(neighborhoods), List.of(), List.of());
    }

    private static Route routeWithPath(long id, String name, List<Coordinate> path, String... neighborhoods) {
        return new Route(id, name, RouteType.URBANA, RouteStatus.ACTIVA, List.of(neighborhoods), List.of(), path);
    }

    @Test
    void elige_la_candidata_con_menor_tiempo_estimado() {
        // Trazados distintos (no List.of() en ambas) para que el mock de travelTimePort
        // pueda distinguir una llamada de la otra por argumentos.
        Route lenta = routeWithPath(1L, "Lenta", List.of(new Coordinate(4.34, -74.36)), "Centro", "Terminal");
        Route rapida = routeWithPath(2L, "Rapida", List.of(new Coordinate(4.35, -74.37)), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(lenta, rapida));
        when(travelTimePort.getTravelTime(ORIGIN, DESTINATION, lenta.path())).thenReturn(Duration.ofMinutes(25));
        when(travelTimePort.getTravelTime(ORIGIN, DESTINATION, rapida.path())).thenReturn(Duration.ofMinutes(12));

        RouteSearchResult result = service().findBestRoute("Centro", ORIGIN, "Terminal", DESTINATION);

        assertThat(result.route()).isEqualTo(rapida);
        assertThat(result.estimatedTime()).isEqualTo(Duration.ofMinutes(12));
    }

    @Test
    void ignora_rutas_suspendidas_y_que_no_conectan_en_el_orden_correcto() {
        Route suspendida = route(1L, "Suspendida", RouteStatus.SUSPENDIDA, "Centro", "Terminal");
        Route sentidoContrario = route(2L, "Contraria", RouteStatus.ACTIVA, "Terminal", "Centro");
        Route valida = route(3L, "Valida", RouteStatus.ACTIVA, "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(suspendida, sentidoContrario, valida));
        when(travelTimePort.getTravelTime(ORIGIN, DESTINATION, valida.path())).thenReturn(Duration.ofMinutes(10));

        RouteSearchResult result = service().findBestRoute("Centro", ORIGIN, "Terminal", DESTINATION);

        assertThat(result.route()).isEqualTo(valida);
    }

    @Test
    void sin_candidatas_lanza_no_route_available() {
        when(routeRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service().findBestRoute("Centro", ORIGIN, "Terminal", DESTINATION))
                .isInstanceOf(NoRouteAvailableException.class);
    }
}
