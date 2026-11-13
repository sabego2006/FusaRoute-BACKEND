package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.InvalidCoordinateException;
import com.fusaroute.domain.exception.InvalidRideException;

import java.time.Instant;

/**
 * Viaje confirmado con "Ya me subi". El mas reciente da el "ultimo destino al que
 * fuiste". No es una busqueda ({@link SearchRecord}): una busqueda es una pregunta,
 * un viaje es lo que el usuario dijo haber tomado.
 *
 * {@code routeName} y {@code signFile} no se guardan con el viaje: se leen de la
 * ruta al consultar, para que un cambio de letrero en el catalogo se vea tambien en
 * el historial. {@code signFile} puede ser nulo (rutas sin letrero fotografiado).
 */
public record Ride(Long id, Long userId, Long routeId, String routeName, String signFile,
                   String destinationLabel, Long destinationPlaceId, Coordinate destination,
                   String originLabel, Instant boardedAt, boolean routeAvailable) {

    public static final int MAX_PER_USER = 10;
    public static final int MAX_LABEL_LENGTH = 120;

    /**
     * Viaje de una ruta disponible. {@code routeAvailable} es falso cuando la ruta esta INACTIVA
     * (soft delete del administrador, RF-11): el viaje se conserva marcado como "ruta ya no disponible".
     */
    public Ride(Long id, Long userId, Long routeId, String routeName, String signFile,
                String destinationLabel, Long destinationPlaceId, Coordinate destination,
                String originLabel, Instant boardedAt) {
        this(id, userId, routeId, routeName, signFile, destinationLabel, destinationPlaceId, destination,
                originLabel, boardedAt, true);
    }

    public Ride {
        if (userId == null) {
            throw new InvalidRideException("El viaje siempre pertenece a un usuario");
        }
        if (routeId == null) {
            throw new InvalidRideException("El viaje necesita la ruta que se tomo");
        }
        if (destination == null) {
            throw new InvalidRideException("El viaje necesita las coordenadas del destino");
        }
        destinationLabel = requireLabel(destinationLabel, "destino");
        originLabel = requireLabel(originLabel, "origen");
        if (boardedAt == null) {
            throw new InvalidRideException("El viaje necesita una fecha");
        }
    }

    /** Arma el viaje desde una ruta que el caso de uso ya valido como activa. */
    public static Ride boarded(Long userId, Route route, String destinationLabel, Long destinationPlaceId,
                               Double destinationLat, Double destinationLng, String originLabel,
                               Instant boardedAt) {
        if (destinationLat == null || destinationLng == null) {
            throw new InvalidRideException("El viaje necesita las coordenadas del destino");
        }
        Coordinate destination;
        try {
            destination = new Coordinate(destinationLat, destinationLng);
        } catch (InvalidCoordinateException e) {
            throw new InvalidRideException(e.getMessage());
        }
        return new Ride(null, userId, route.id(), route.name(), route.signFile(),
                destinationLabel, destinationPlaceId, destination, originLabel, boardedAt);
    }

    private static String requireLabel(String value, String campo) {
        if (value == null || value.isBlank()) {
            throw new InvalidRideException("El " + campo + " del viaje no puede ir vacio");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LABEL_LENGTH) {
            // Se rechaza en vez de recortar, igual que el destino favorito.
            throw new InvalidRideException(
                    "El " + campo + " del viaje no puede pasar de " + MAX_LABEL_LENGTH + " caracteres");
        }
        return trimmed;
    }
}
