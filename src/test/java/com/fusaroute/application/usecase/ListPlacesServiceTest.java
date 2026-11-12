package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.in.ListPlacesUseCase.PlaceSummary;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ListPlacesServiceTest {

    private static final double LAT = 4.34;

    private static Route route(long id, RouteStatus status, List<Coordinate> path) {
        return new Route(id, "Ruta " + id, null, RouteType.URBANA, status, List.of(), List.of(), path);
    }

    private static final List<Coordinate> RECTA = List.of(new Coordinate(LAT, -74.40), new Coordinate(LAT, -74.34));

    private static Place place(long id, String name, double lat, double lng) {
        return new Place(id, name.toLowerCase().replace(' ', '-'), name, new Coordinate(lat, lng), List.of());
    }

    private ListPlacesService service(List<Place> places, List<Route> routes) {
        PlaceRepositoryPort placeRepository = new PlaceRepositoryPort() {
            @Override
            public List<Place> findAll() {
                return places;
            }

            @Override
            public Optional<Place> findById(Long id) {
                return places.stream().filter(p -> p.id().equals(id)).findFirst();
            }
        };
        RouteRepositoryPort routeRepository = new RouteRepositoryPort() {
            @Override
            public List<Route> findAll() {
                return routes;
            }

            @Override
            public Optional<Route> findById(Long id) {
                return Optional.empty();
            }
        };
        return new ListPlacesService(placeRepository, routeRepository);
    }

    @Test
    void ordena_por_nombre_con_el_idioma_y_no_por_codigo_de_caracter() {
        List<Place> places = List.of(place(1, "Zona Rosa", LAT, -74.38), place(2, "Éxito", LAT, -74.38),
                place(3, "Galería", LAT, -74.38), place(4, "Batallón", LAT, -74.38));

        List<String> names = service(places, List.of()).listPlaces().stream()
                .map(summary -> summary.place().name()).toList();

        assertThat(names).containsExactly("Batallón", "Éxito", "Galería", "Zona Rosa");
    }

    @Test
    void no_cuenta_las_rutas_inactivas() {
        List<Place> places = List.of(place(1, "Sobre la ruta", LAT, -74.38));
        List<Route> routes = List.of(route(10, RouteStatus.ACTIVA, RECTA), route(11, RouteStatus.INACTIVA, RECTA));

        assertThat(service(places, routes).listPlaces()).extracting(PlaceSummary::routeCount).containsExactly(1);
    }

    @Test
    void cuenta_las_rutas_activas_con_trazado_a_menos_de_800_m_en_linea_recta() {
        List<Place> places = List.of(
                place(1, "Sobre la ruta", LAT, -74.38),
                place(2, "A 600 m", LAT + 0.0054, -74.38),
                place(3, "A 1 km", LAT + 0.009, -74.38));
        List<Route> routes = List.of(
                route(10, RouteStatus.ACTIVA, RECTA),
                route(11, RouteStatus.ACTIVA, RECTA),
                route(12, RouteStatus.SUSPENDIDA, RECTA),
                route(13, RouteStatus.ACTIVA, List.of()));

        List<PlaceSummary> result = service(places, routes).listPlaces();

        assertThat(result).extracting(s -> s.place().name(), PlaceSummary::routeCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A 1 km", 0),
                        org.assertj.core.groups.Tuple.tuple("A 600 m", 2),
                        org.assertj.core.groups.Tuple.tuple("Sobre la ruta", 2));
    }
}
