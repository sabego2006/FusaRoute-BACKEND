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

    // --- SCRUM-192: el conteo se reutiliza solo mientras el catalogo sea el mismo ---

    /** Puerto de rutas al que se le puede cambiar la lista, como hace el catalogo cuando el admin escribe. */
    private static final class RutasCambiantes implements RouteRepositoryPort {
        private List<Route> actual;

        RutasCambiantes(List<Route> inicial) {
            this.actual = inicial;
        }

        @Override
        public List<Route> findAll() {
            return actual;
        }

        @Override
        public Optional<Route> findById(Long id) {
            return Optional.empty();
        }
    }

    private static PlaceRepositoryPort lugares(List<Place> places) {
        return new PlaceRepositoryPort() {
            @Override
            public List<Place> findAll() {
                return places;
            }

            @Override
            public Optional<Place> findById(Long id) {
                return Optional.empty();
            }
        };
    }

    @Test
    void con_el_mismo_catalogo_reutiliza_el_resultado() {
        ListPlacesService service = service(List.of(place(1, "Sobre la ruta", LAT, -74.38)),
                List.of(route(10, RouteStatus.ACTIVA, RECTA)));

        assertThat(service.listPlaces()).isSameAs(service.listPlaces());
    }

    @Test
    void suspender_una_ruta_cambia_el_conteo_en_la_siguiente_consulta() {
        RutasCambiantes rutas = new RutasCambiantes(List.of(route(10, RouteStatus.ACTIVA, RECTA)));
        ListPlacesService service = new ListPlacesService(lugares(List.of(place(1, "Sobre la ruta", LAT, -74.38))), rutas);
        assertThat(service.listPlaces()).extracting(PlaceSummary::routeCount).containsExactly(1);

        rutas.actual = List.of(route(10, RouteStatus.SUSPENDIDA, RECTA));
        assertThat(service.listPlaces()).extracting(PlaceSummary::routeCount).containsExactly(0);

        rutas.actual = List.of(route(10, RouteStatus.ACTIVA, RECTA));
        assertThat(service.listPlaces()).extracting(PlaceSummary::routeCount).containsExactly(1);
    }

    @Test
    void modificar_el_trazado_de_una_ruta_cambia_el_conteo_en_la_siguiente_consulta() {
        RutasCambiantes rutas = new RutasCambiantes(List.of(route(10, RouteStatus.ACTIVA, RECTA)));
        ListPlacesService service = new ListPlacesService(lugares(List.of(place(1, "Sobre la ruta", LAT, -74.38))), rutas);
        assertThat(service.listPlaces()).extracting(PlaceSummary::routeCount).containsExactly(1);

        // La ruta ahora pasa a mas de 5 km del lugar.
        rutas.actual = List.of(route(10, RouteStatus.ACTIVA,
                List.of(new Coordinate(LAT + 0.05, -74.40), new Coordinate(LAT + 0.05, -74.34))));

        assertThat(service.listPlaces()).extracting(PlaceSummary::routeCount).containsExactly(0);
    }

    @Test
    void una_lista_igual_pero_nueva_se_recalcula() {
        // Solo la MISMA instancia garantiza el mismo contenido; dos listas iguales se tratan como distintas.
        RutasCambiantes rutas = new RutasCambiantes(List.of(route(10, RouteStatus.ACTIVA, RECTA)));
        ListPlacesService service = new ListPlacesService(lugares(List.of(place(1, "Sobre la ruta", LAT, -74.38))), rutas);
        List<PlaceSummary> antes = service.listPlaces();

        rutas.actual = List.of(route(10, RouteStatus.ACTIVA, RECTA));

        assertThat(service.listPlaces()).isNotSameAs(antes).isEqualTo(antes);
    }

    @Test
    void peticiones_simultaneas_con_el_catalogo_recien_cambiado_calculan_una_sola_vez() throws Exception {
        // La rafaga del QR: treinta lectores a la vez sobre un catalogo que nadie ha calculado todavia.
        ListPlacesService service = service(List.of(place(1, "Sobre la ruta", LAT, -74.38)),
                List.of(route(10, RouteStatus.ACTIVA, RECTA)));
        List<PlaceSummary> esperado = service.listPlaces();

        ListPlacesService frio = service(List.of(place(1, "Sobre la ruta", LAT, -74.38)),
                List.of(route(10, RouteStatus.ACTIVA, RECTA)));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(30);
        try {
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<List<PlaceSummary>>> respuestas = new java.util.ArrayList<>();
            for (int i = 0; i < 30; i++) {
                respuestas.add(pool.submit(() -> {
                    salida.await();
                    return frio.listPlaces();
                }));
            }
            salida.countDown();
            List<PlaceSummary> primera = respuestas.get(0).get();
            for (java.util.concurrent.Future<List<PlaceSummary>> respuesta : respuestas) {
                // Todas reciben EL MISMO resultado: se calculo una vez y las demas lo reutilizaron.
                assertThat(respuesta.get()).isSameAs(primera).isEqualTo(esperado);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
