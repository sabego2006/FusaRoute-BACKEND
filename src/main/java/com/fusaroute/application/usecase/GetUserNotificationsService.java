package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.BoardingPoint;
import com.fusaroute.domain.model.FavoriteDestination;
import com.fusaroute.domain.model.NotificationReason;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.Ride;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.UserNotification;
import com.fusaroute.domain.port.in.GetUserNotificationsUseCase;
import com.fusaroute.domain.port.out.ActiveSuspensionsPort;
import com.fusaroute.domain.port.out.FavoriteDestinationRepositoryPort;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import com.fusaroute.domain.port.out.RideRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Avisos de ruta suspendida del usuario (RF-12, SCRUM-25). Se calculan al leer: no hay tabla de
 * notificaciones, ni fan-out al suspender, ni estado de "leido" en el servidor.
 *
 * <p>Una suspension le avisa al usuario por uno de dos motivos (definicion pendiente de aprobacion de la
 * PO, ver {@code docs/decisiones/rf12-destinatarios-del-aviso.md}):
 * <ul>
 *   <li>{@link NotificationReason#FAVORITE}: la ruta sirve su destino favorito. Ni el favorito ni las
 *       busquedas guardadas llevan {@code route_id}, asi que "sirve" se decide con lo que SI hay: el favorito
 *       coincide con un barrio del recorrido (misma regla del buscador) o con un lugar canonico que queda a
 *       distancia de caminata del trazado.</li>
 *   <li>{@link NotificationReason#FREQUENT}: la ruta esta en al menos {@value #FREQUENT_MIN_RIDES} de sus
 *       ultimos {@value #FREQUENT_WINDOW} viajes confirmados ({@code ride_history}, lo unico que guarda la
 *       ruta).</li>
 * </ul>
 * Una suspension genera como maximo un aviso por usuario; si cumple los dos motivos sale FAVORITE.
 */
public class GetUserNotificationsService implements GetUserNotificationsUseCase {

    /** Ultimos viajes que se miran para decidir si una ruta es "frecuente". */
    static final int FREQUENT_WINDOW = 6;

    /** Viajes minimos de esa ventana para que la ruta cuente como frecuente. */
    static final int FREQUENT_MIN_RIDES = 3;

    private final ActiveSuspensionsPort suspensions;
    private final RouteRepositoryPort routes;
    private final FavoriteDestinationRepositoryPort favorites;
    private final PlaceRepositoryPort places;
    private final RideRepositoryPort rides;
    private final Clock clock;

    public GetUserNotificationsService(ActiveSuspensionsPort suspensions, RouteRepositoryPort routes,
                                       FavoriteDestinationRepositoryPort favorites, PlaceRepositoryPort places,
                                       RideRepositoryPort rides, Clock clock) {
        this.suspensions = suspensions;
        this.routes = routes;
        this.favorites = favorites;
        this.places = places;
        this.rides = rides;
        this.clock = clock;
    }

    @Override
    public List<UserNotification> notificationsOf(Long userId) {
        List<RouteSuspension> effective = suspensions.findEffectiveAt(clock.instant());
        if (effective.isEmpty()) {
            return List.of();
        }
        // Solo las rutas que el publico ve como suspendidas: una suspension vigente sobre una ruta que luego
        // se desactivo (INACTIVA) no es un aviso de "vuelve pronto".
        Map<Long, Route> suspendedRoutes = new HashMap<>();
        for (Route route : routes.findAll()) {
            if (route.status() == RouteStatus.SUSPENDIDA && route.id() != null) {
                suspendedRoutes.put(route.id(), route);
            }
        }
        Optional<FavoriteDestination> favorite = favorites.findByUser(userId);
        List<Place> favoritePlaces = favorite.map(f -> placesNamed(f.destination())).orElse(List.of());
        Map<Long, Integer> recentRides = recentRidesPerRoute(userId);

        List<UserNotification> notifications = new ArrayList<>();
        for (RouteSuspension suspension : effective) {
            Route route = suspendedRoutes.get(suspension.routeId());
            if (route == null) {
                continue;
            }
            if (favorite.isPresent() && serves(route, favorite.get().destination(), favoritePlaces)) {
                notifications.add(UserNotification.routeSuspended(suspension, NotificationReason.FAVORITE));
            } else if (recentRides.getOrDefault(route.id(), 0) >= FREQUENT_MIN_RIDES) {
                notifications.add(UserNotification.routeSuspended(suspension, NotificationReason.FREQUENT));
            }
        }
        return notifications;
    }

    /** Lugares canonicos cuyo nombre o alias es el texto del favorito. Vacio sin favorito. */
    private List<Place> placesNamed(String name) {
        return places.findAll().stream().filter(place -> place.matchesName(name)).toList();
    }

    /**
     * La ruta sirve el destino si lo nombra uno de sus barrios, o si un lugar con ese nombre queda a
     * distancia de caminata de su trazado (el mismo umbral de 800 m de la busqueda y de /api/places).
     */
    private static boolean serves(Route route, String destination, List<Place> namedPlaces) {
        if (route.indexOfPlace(destination).isPresent()) {
            return true;
        }
        return route.hasPath() && namedPlaces.stream().anyMatch(place ->
                route.distanceToPathInMeters(place.coordinate()).orElse(Double.MAX_VALUE)
                        <= BoardingPoint.MAX_WALKING_DISTANCE_METERS);
    }

    private Map<Long, Integer> recentRidesPerRoute(Long userId) {
        Map<Long, Integer> perRoute = new HashMap<>();
        for (Ride ride : rides.findRecentByUser(userId, FREQUENT_WINDOW)) {
            perRoute.merge(ride.routeId(), 1, Integer::sum);
        }
        return perRoute;
    }
}
