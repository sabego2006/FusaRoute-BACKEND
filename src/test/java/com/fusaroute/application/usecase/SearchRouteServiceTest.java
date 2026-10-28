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
import static org.mockito.Mockito.verifyNoInteractions;
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

    /** Trazado que pasa por el origen y el destino, asi la ultima milla no lo descarta. */
    private static List<Coordinate> trazadoQuePasaPorAmbos(double desvio) {
        return List.of(
                new Coordinate(ORIGIN.latitude() + desvio, ORIGIN.longitude() + desvio),
                new Coordinate(DESTINATION.latitude() + desvio, DESTINATION.longitude() + desvio));
    }

    @Test
    void elige_la_candidata_con_menor_tiempo_estimado() {
        // Trazados distintos (no List.of() en ambas) para que el mock de travelTimePort
        // pueda distinguir una llamada de la otra por argumentos.
        Route lenta = routeWithPath(1L, "Lenta", trazadoQuePasaPorAmbos(0), "Centro", "Terminal");
        Route rapida = routeWithPath(2L, "Rapida", trazadoQuePasaPorAmbos(0.0001), "Centro", "Terminal");
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

    @Test
    void el_resultado_trae_el_punto_de_abordaje_y_el_de_bajada() {
        Route conTrazado = routeWithPath(1L, "Con trazado", trazadoQuePasaPorAmbos(0), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(conTrazado));
        when(travelTimePort.getTravelTime(ORIGIN, DESTINATION, conTrazado.path()))
                .thenReturn(Duration.ofMinutes(14));

        RouteSearchResult result = service().findBestRoute("Centro", ORIGIN, "Terminal", DESTINATION);

        assertThat(result.boarding()).isPresent();
        assertThat(result.dropOff()).isPresent();
        assertThat(result.boarding().orElseThrow().distanceInMeters()).isLessThan(50);
    }

    @Test
    void descarta_la_candidata_que_obliga_a_caminar_mas_de_ochocientos_metros() {
        // El trazado corre a ~1.1 km al norte: conecta los barrios, pero no se puede
        // llegar a el a pie desde el origen.
        Route lejana = routeWithPath(1L, "Lejana", List.of(
                new Coordinate(ORIGIN.latitude() + 0.01, ORIGIN.longitude()),
                new Coordinate(DESTINATION.latitude() + 0.01, DESTINATION.longitude())),
                "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(lejana));

        assertThatThrownBy(() -> service().findBestRoute("Centro", ORIGIN, "Terminal", DESTINATION))
                .isInstanceOf(NoRouteAvailableException.class);
    }

    @Test
    void offline_elige_la_de_menor_recorrido_y_no_consulta_a_google() {
        // Las dos conectan los mismos barrios y arrancan y terminan en el mismo punto;
        // la "larga" se desvia por el noroeste antes de llegar.
        Route corta = routeWithPath(1L, "Corta", List.of(ORIGIN, DESTINATION), "Centro", "Terminal");
        Route larga = routeWithPath(2L, "Larga",
                List.of(ORIGIN, new Coordinate(4.3500, -74.3800), DESTINATION), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(larga, corta));

        RouteSearchResult result = service().findBestRouteOffline("Centro", ORIGIN, "Terminal", DESTINATION);

        assertThat(result.route()).isEqualTo(corta);
        assertThat(result.travelTime()).isEmpty();
        assertThat(result.travelDistance()).isPresent();
        // Sin red no se pregunta el tiempo: ese es el punto del modo offline.
        verifyNoInteractions(travelTimePort);
    }

    @Test
    void offline_deja_de_ultimas_las_rutas_sin_trazado() {
        // No hay con que medirlas, asi que solo ganan si ninguna otra pudo compararse.
        Route sinTrazado = route(1L, "Sin trazado", RouteStatus.ACTIVA, "Centro", "Terminal");
        Route conTrazado = routeWithPath(2L, "Con trazado", List.of(ORIGIN, DESTINATION), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(sinTrazado, conTrazado));

        RouteSearchResult result = service().findBestRouteOffline("Centro", ORIGIN, "Terminal", DESTINATION);

        assertThat(result.route()).isEqualTo(conTrazado);
    }

    @Test
    void offline_con_solo_rutas_sin_trazado_la_sugiere_igual_pero_sin_distancia() {
        Route sinTrazado = route(1L, "Sin trazado", RouteStatus.ACTIVA, "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(sinTrazado));

        RouteSearchResult result = service().findBestRouteOffline("Centro", ORIGIN, "Terminal", DESTINATION);

        assertThat(result.route()).isEqualTo(sinTrazado);
        // Vacio y no cero: no se midio nada, y un 0 se leeria como "ya estas encima".
        assertThat(result.travelDistance()).isEmpty();
    }

    @Test
    void offline_sin_candidatas_lanza_no_route_available() {
        when(routeRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service().findBestRouteOffline("Centro", ORIGIN, "Terminal", DESTINATION))
                .isInstanceOf(NoRouteAvailableException.class);
    }

    @Test
    void una_ruta_sin_trazado_sigue_siendo_candidata() {
        // Hoy 7 de las 9 rutas sembradas no tienen GeoJSON todavia. Se sugieren igual,
        // solo que sin ultima milla.
        Route sinTrazado = route(1L, "Sin trazado", RouteStatus.ACTIVA, "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(sinTrazado));
        when(travelTimePort.getTravelTime(ORIGIN, DESTINATION, List.of()))
                .thenReturn(Duration.ofMinutes(20));

        RouteSearchResult result = service().findBestRoute("Centro", ORIGIN, "Terminal", DESTINATION);

        assertThat(result.route()).isEqualTo(sinTrazado);
        assertThat(result.boarding()).isEmpty();
        assertThat(result.dropOff()).isEmpty();
    }
}
