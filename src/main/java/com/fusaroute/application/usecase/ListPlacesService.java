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
 */
public class ListPlacesService implements ListPlacesUseCase {

    private final PlaceRepositoryPort placeRepository;
    private final RouteRepositoryPort routeRepository;

    public ListPlacesService(PlaceRepositoryPort placeRepository, RouteRepositoryPort routeRepository) {
        this.placeRepository = placeRepository;
        this.routeRepository = routeRepository;
    }

    @Override
    public List<PlaceSummary> listPlaces() {
        List<Route> routes = routeRepository.findAll().stream()
                .filter(Route::isActive)
                .filter(Route::hasPath)
                .toList();
        // Collator del idioma: "Éxito" y "Galería" se ordenan donde el usuario espera, no al final.
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es-CO"));
        return placeRepository.findAll().stream()
                .sorted(Comparator.comparing(Place::name, collator).thenComparing(Place::slug))
                .map(place -> new PlaceSummary(place, (int) routes.stream()
                        .filter(route -> route.distanceToPathInMeters(place.coordinate()).orElse(Double.MAX_VALUE)
                                <= BoardingPoint.MAX_WALKING_DISTANCE_METERS)
                        .count()))
                .toList();
    }
}
