package com.fusaroute.domain.exception;

import com.fusaroute.domain.model.NearestRoute;
import com.fusaroute.domain.model.SuspendedRoute;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Excepción de negocio lanzada cuando no se encuentra ninguna ruta que conecte
 * el origen y el destino solicitados.
 *
 * Desde P1 puede llevar la ruta con trazado mas cercana a cada punta ({@link #nearestToOrigin()},
 * {@link #nearestToDestination()}), para que el cliente le diga al usuario cuanto le falta.
 *
 * Desde RF-12 puede llevar tambien las rutas que SI servirian el trayecto pero estan suspendidas
 * ({@link #suspendedCandidates()}): "no hay ruta" porque la unica opcion esta suspendida no es lo
 * mismo que "no hay ruta", y el mensaje lo dice con la fecha en que vuelve.
 */
public class NoRouteAvailableException extends RuntimeException {

    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern(
            "'el' d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-CO"));

    private final String originLabel;
    private final String destinationLabel;
    private final transient NearestRoute nearestToOrigin;
    private final transient NearestRoute nearestToDestination;
    private final transient List<SuspendedRoute> suspendedCandidates;

    public NoRouteAvailableException(String origin, String destination) {
        this(origin, destination, null, null);
    }

    public NoRouteAvailableException(String origin, String destination,
                                     NearestRoute nearestToOrigin, NearestRoute nearestToDestination) {
        this(origin, destination, nearestToOrigin, nearestToDestination, List.of(), ZoneId.of("America/Bogota"));
    }

    /**
     * @param suspendedCandidates rutas candidatas hoy suspendidas, con su fin de ventana; vacia si no hubo
     * @param zone zona en que se escribe la fecha de regreso dentro del mensaje (America/Bogota en produccion)
     */
    public NoRouteAvailableException(String origin, String destination,
                                     NearestRoute nearestToOrigin, NearestRoute nearestToDestination,
                                     List<SuspendedRoute> suspendedCandidates, ZoneId zone) {
        super(message(origin, destination, suspendedCandidates, zone));
        this.originLabel = origin;
        this.destinationLabel = destination;
        this.nearestToOrigin = nearestToOrigin;
        this.nearestToDestination = nearestToDestination;
        this.suspendedCandidates = suspendedCandidates == null ? List.of() : List.copyOf(suspendedCandidates);
    }

    private static String message(String origin, String destination, List<SuspendedRoute> suspended, ZoneId zone) {
        String base = String.format("No hay rutas disponibles que conecten %s con %s", origin, destination);
        if (suspended == null || suspended.isEmpty()) {
            return base;
        }
        if (suspended.size() == 1) {
            SuspendedRoute only = suspended.get(0);
            return base + ". La ruta " + only.routeName() + " está suspendida hasta " + until(only, zone) + ".";
        }
        return base + ". Las rutas " + suspended.stream()
                .map(s -> s.routeName() + " (hasta " + until(s, zone) + ")")
                .collect(Collectors.joining(", ")) + " están suspendidas.";
    }

    private static String until(SuspendedRoute route, ZoneId zone) {
        return UNTIL.format(route.endsAt().atZone(zone));
    }

    /** Origen tal como se muestra (lugar canonico o "Cerca de ..."). */
    public String originLabel() {
        return originLabel;
    }

    /** Destino tal como se muestra (lugar canonico o "Cerca de ..."). */
    public String destinationLabel() {
        return destinationLabel;
    }

    /** Ruta mas cercana al origen; {@code null} si no se pudo calcular. */
    public NearestRoute nearestToOrigin() {
        return nearestToOrigin;
    }

    /** Ruta mas cercana al destino; {@code null} si no se pudo calcular. */
    public NearestRoute nearestToDestination() {
        return nearestToDestination;
    }

    /** Rutas que servirian el trayecto pero estan suspendidas ahora, la que vuelve antes primero; vacia si no hay. */
    public List<SuspendedRoute> suspendedCandidates() {
        return suspendedCandidates;
    }
}
