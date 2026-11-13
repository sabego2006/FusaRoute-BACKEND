package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.application.usecase.GetRouteDetailService;
import com.fusaroute.application.usecase.GetRouteFareService;
import com.fusaroute.application.usecase.ListActiveRoutesService;
import com.fusaroute.application.usecase.ListPlacesService;
import com.fusaroute.application.usecase.RideHistoryService;
import com.fusaroute.application.usecase.SearchRouteService;
import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.SearchPoint;
import com.fusaroute.domain.port.in.RecordRideCommand;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import com.fusaroute.domain.port.out.RideRepositoryPort;
import com.fusaroute.domain.port.out.RouteSpeedPort;
import com.fusaroute.domain.port.out.TravelTimePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Una ruta con ventana de suspension vigente desaparece de TODO lo publico y reaparece sola (RF-12): el
 * catalogo, el detalle, la tarifa, los lugares con servicio, "Ya me subi", la busqueda en linea y la busqueda
 * sin conexion. Se prueba con el adaptador real de rutas y los casos de uso reales encima; solo la consulta SQL
 * del predicado se simula (con la misma regla de ventana semiabierta), porque no hay Docker en este test: la
 * consulta real la ejercita {@code RouteSuspensionPersistenceAdapterIT} y la prueba en vivo contra DEV.
 */
class SuspensionVisibilityTest {

    private static final Instant INICIO = Instant.parse("2026-11-20T13:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-22T13:00:00Z");
    private static final double LAT = 4.34;

    private static final Place OESTE = new Place(1L, "oeste", "Oeste", new Coordinate(LAT, -74.395), List.of());
    private static final Place ESTE = new Place(2L, "este", "Este", new Coordinate(LAT, -74.36), List.of());

    /** Reloj que se puede mover: lo que hace que la ruta "vuelva sola" sin que nadie toque la base. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Bogota");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock(INICIO.minus(Duration.ofDays(2)));
    private final List<RouteSuspension> suspensions = new ArrayList<>();
    private RouteJpaEntity entity;

    private RoutePersistenceAdapter routes;
    private TravelTimePort travelTime;
    private RideRepositoryPort rides;
    private PlaceRepositoryPort places;

    @BeforeEach
    void setUp() {
        entity = ruta(RouteStatus.ACTIVA);
        SpringDataRouteRepository routeRepo = mock(SpringDataRouteRepository.class);
        when(routeRepo.findAll()).thenAnswer(inv -> List.of(entity));
        when(routeRepo.findById(10L)).thenAnswer(inv -> Optional.of(entity));

        SpringDataRouteSuspensionRepository suspensionRepo = mock(SpringDataRouteSuspensionRepository.class);
        // El mismo predicado que la consulta SQL: starts_at <= ahora < ends_at y sin levantar.
        when(suspensionRepo.findEffectiveRouteIdsAt(any())).thenAnswer(inv -> {
            Instant ahora = inv.getArgument(0);
            return suspensions.stream().filter(s -> s.isEffectiveAt(ahora)).map(RouteSuspension::routeId).toList();
        });
        routes = new RoutePersistenceAdapter(routeRepo, suspensionRepo, clock);

        travelTime = mock(TravelTimePort.class);
        when(travelTime.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(12));
        rides = mock(RideRepositoryPort.class);
        when(rides.saveKeepingLatest(any(), anyInt())).thenAnswer(inv -> inv.getArgument(0));
        places = mock(PlaceRepositoryPort.class);
        when(places.findAll()).thenReturn(List.of(OESTE, ESTE));
        when(places.findById(1L)).thenReturn(Optional.of(OESTE));
        when(places.findById(2L)).thenReturn(Optional.of(ESTE));

        suspensions.add(new RouteSuspension(1L, 10L, "Oeste - Este", INICIO, FIN, "Obras", null));
    }

    private static RouteJpaEntity ruta(RouteStatus estado) {
        RouteJpaEntity e = new RouteJpaEntity();
        ReflectionTestUtils.setField(e, "id", 10L);
        ReflectionTestUtils.setField(e, "name", "Oeste - Este");
        ReflectionTestUtils.setField(e, "type", RouteType.URBANA);
        ReflectionTestUtils.setField(e, "status", estado);
        ReflectionTestUtils.setField(e, "neighborhoods", Map.of(0, "Oeste", 1, "Este"));
        FareJpaEntity fare = new FareJpaEntity();
        ReflectionTestUtils.setField(fare, "amount", new BigDecimal("2600.00"));
        ReflectionTestUtils.setField(fare, "orderIndex", 0);
        ReflectionTestUtils.setField(fare, "validFrom", LocalDate.of(2026, 2, 5));
        ReflectionTestUtils.setField(e, "fares", List.of(fare));
        StringBuilder coords = new StringBuilder();
        for (int i = 0; i <= 12; i++) {
            coords.append(i == 0 ? "" : ",").append("[").append(-74.40 + i * 0.005).append(",").append(LAT).append("]");
        }
        ReflectionTestUtils.setField(e, "pathGeoJson", "{\"type\":\"LineString\",\"coordinates\":[" + coords + "]}");
        return e;
    }

    private SearchRouteService search() {
        RouteSpeedPort speeds = new RouteSpeedPort() {
            @Override
            public Map<Long, List<Double>> recentObservations(int perRoute) {
                return Map.of();
            }

            @Override
            public void record(long routeId, double observedKmh) {
                // Sin calibracion en esta prueba.
            }
        };
        return new SearchRouteService(routes, places, travelTime, List::of, speeds, new SimpleMeterRegistry(), clock,
                event -> { }, now -> suspensions.stream().filter(s -> s.isEffectiveAt(now)).toList());
    }

    private List<Route> catalogo() {
        return new ListActiveRoutesService(routes, clock).listActive();
    }

    private static SearchPoint at(Place place) {
        return new SearchPoint.OfPlace(place.id());
    }

    private void assertVisible() {
        assertThat(catalogo()).extracting(Route::id).containsExactly(10L);
        assertThat(new GetRouteDetailService(routes, clock).getById(10L).id()).isEqualTo(10L);
        assertThat(new GetRouteFareService(routes, clock).getByRouteId(10L).id()).isEqualTo(10L);
        assertThat(new ListPlacesService(places, routes).listPlaces()).extracting(p -> p.routeCount())
                .containsExactly(1, 1);
        assertThat(new RideHistoryService(rides, routes, clock).record(ride()).routeId()).isEqualTo(10L);
        assertThat(search().findBestRoute(at(OESTE), at(ESTE)).route().id()).isEqualTo(10L);
        assertThat(search().findBestRouteOffline(at(OESTE), at(ESTE)).route().id()).isEqualTo(10L);
    }

    private void assertHidden() {
        assertThat(catalogo()).isEmpty();
        assertThatThrownBy(() -> new GetRouteDetailService(routes, clock).getById(10L))
                .isInstanceOf(RouteNotFoundException.class);
        assertThatThrownBy(() -> new GetRouteFareService(routes, clock).getByRouteId(10L))
                .isInstanceOf(RouteNotFoundException.class);
        assertThat(new ListPlacesService(places, routes).listPlaces()).extracting(p -> p.routeCount())
                .containsExactly(0, 0);
        assertThatThrownBy(() -> new RideHistoryService(rides, routes, clock).record(ride()))
                .isInstanceOf(RouteNotFoundException.class);
        assertThatThrownBy(() -> search().findBestRoute(at(OESTE), at(ESTE)))
                .isInstanceOfSatisfying(NoRouteAvailableException.class,
                        e -> assertThat(e.suspendedCandidates()).hasSize(1));
        assertThatThrownBy(() -> search().findBestRouteOffline(at(OESTE), at(ESTE)))
                .isInstanceOfSatisfying(NoRouteAvailableException.class,
                        e -> assertThat(e.suspendedCandidates()).extracting(s -> s.routeId()).containsExactly(10L));
    }

    private static RecordRideCommand ride() {
        return new RecordRideCommand(7L, 10L, "Este", 2L, LAT, -74.36, "Oeste");
    }

    @Test
    void antes_de_la_ventana_la_ruta_se_ve_en_todo() {
        clock.set(INICIO.minusMillis(1));

        assertVisible();
    }

    @Test
    void justo_en_startsAt_la_ruta_desaparece_de_todo_lo_publico() {
        clock.set(INICIO);

        assertHidden();
    }

    @Test
    void dentro_de_la_ventana_sigue_oculta() {
        clock.set(INICIO.plus(Duration.ofDays(1)));

        assertHidden();
    }

    @Test
    void un_instante_antes_de_endsAt_sigue_oculta() {
        clock.set(FIN.minusMillis(1));

        assertHidden();
    }

    @Test
    void justo_en_endsAt_reaparece_sola_en_todo_sin_tocar_la_base() {
        clock.set(FIN);

        assertVisible();
        // routes.status nunca cambio: la ruta sigue ACTIVA en la fila.
        assertThat(entity.getStatus()).isEqualTo(RouteStatus.ACTIVA);
    }

    @Test
    void avanzar_el_reloj_la_oculta_y_luego_la_devuelve() {
        clock.set(INICIO.minus(Duration.ofHours(1)));
        assertVisible();
        clock.set(INICIO.plus(Duration.ofHours(1)));
        assertHidden();
        clock.set(FIN.plus(Duration.ofHours(1)));
        assertVisible();
    }

    @Test
    void al_levantarla_a_mano_reaparece_de_inmediato() {
        clock.set(INICIO.plus(Duration.ofDays(1)));
        assertHidden();

        suspensions.replaceAll(s -> s.lifted(clock.instant()));

        assertVisible();
    }

    @Test
    void una_ruta_inactiva_sigue_inactiva_aunque_tenga_una_ventana_vigente() {
        entity = ruta(RouteStatus.INACTIVA);
        clock.set(INICIO.plus(Duration.ofDays(1)));

        assertThat(routes.findById(10L)).get().extracting(Route::status).isEqualTo(RouteStatus.INACTIVA);
    }

    @Test
    void la_suspension_de_otra_ruta_no_afecta_a_esta() {
        suspensions.replaceAll(s -> new RouteSuspension(1L, 99L, "Otra", INICIO, FIN, null, null));
        clock.set(INICIO.plus(Duration.ofDays(1)));

        assertVisible();
    }

    @Test
    void la_lectura_publica_presenta_la_ruta_como_suspendida_y_no_activa() {
        // El adaptador presenta SUSPENDIDA; es el unico lugar donde se decide, y Route::isActive hace el resto.
        clock.set(INICIO.plus(Duration.ofDays(1)));

        assertThat(routes.findById(10L)).get().extracting(Route::status).isEqualTo(RouteStatus.SUSPENDIDA);
        assertThat(routes.findAll()).singleElement().extracting(Route::isActive).isEqualTo(false);
    }
}
