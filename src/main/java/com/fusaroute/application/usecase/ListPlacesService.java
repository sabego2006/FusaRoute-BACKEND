package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.BoardingPoint;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.port.in.ListPlacesUseCase;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Catalogo de lugares para el selector de origen y destino. {@code routeCount} dice cuantas rutas
 * activas con trazado pasan a distancia de caminata (800 m en linea recta) del lugar, para que el
 * selector pueda mostrar de un vistazo que lugares tienen servicio.
 *
 * <p>Contar las rutas de cada lugar es medir la distancia de 135 lugares a los trazados de todas las rutas:
 * el calculo mas caro de las lecturas publicas (SCRUM-192). El resultado solo depende de la lista de rutas y
 * de la lista de lugares, asi que se guarda y se reutiliza mientras los puertos devuelvan LAS MISMAS listas
 * (misma instancia, no listas iguales). Los modelos son inmutables: la misma instancia implica el mismo
 * contenido, y cualquier cambio (una ruta modificada, suspendida o desactivada) llega como una lista nueva
 * y obliga a recalcular. Si un puerto no reutiliza su lista, simplemente se recalcula siempre, como antes.
 */
public class ListPlacesService implements ListPlacesUseCase {

    private final PlaceRepositoryPort placeRepository;
    private final RouteRepositoryPort routeRepository;

    private volatile Memo memo;

    public ListPlacesService(PlaceRepositoryPort placeRepository, RouteRepositoryPort routeRepository) {
        this.placeRepository = placeRepository;
        this.routeRepository = routeRepository;
    }

    @Override
    public List<PlaceSummary> listPlaces() {
        List<Route> allRoutes = routeRepository.findAll();
        List<Place> places = placeRepository.findAll();
        Memo last = memo;
        // Identidad a proposito, no equals: comparar dos catalogos completos costaria lo que se quiere ahorrar.
        if (last != null && last.routes == allRoutes && last.places == places) {
            return last.summaries;
        }
        List<PlaceSummary> summaries = summarize(allRoutes, places);
        memo = new Memo(allRoutes, places, summaries);
        return summaries;
    }

    private static List<PlaceSummary> summarize(List<Route> allRoutes, List<Place> places) {
        List<Route> routes = allRoutes.stream()
                .filter(Route::isActive)
                .filter(Route::hasPath)
                .toList();
        // Collator del idioma: "Éxito" y "Galería" se ordenan donde el usuario espera, no al final.
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es-CO"));
        return places.stream()
                .sorted(Comparator.comparing(Place::name, collator).thenComparing(Place::slug))
                .map(place -> new PlaceSummary(place, (int) routes.stream()
                        .filter(route -> route.distanceToPathInMeters(place.coordinate()).orElse(Double.MAX_VALUE)
                                <= BoardingPoint.MAX_WALKING_DISTANCE_METERS)
                        .count()))
                .toList();
    }

    private record Memo(List<Route> routes, List<Place> places, List<PlaceSummary> summaries) {
    }
}
