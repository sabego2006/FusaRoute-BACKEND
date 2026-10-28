package com.fusaroute.domain.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Ruta de buseta: un recorrido directo de un solo tramo, con sus barrios en orden
 * de recorrido y sus tarifas. Ida y vuelta son dos rutas independientes.
 */
public record Route(Long id, String name, RouteType type, RouteStatus status,
                    List<String> neighborhoods, List<Fare> fares, List<Coordinate> path) {

    public Route {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        neighborhoods = List.copyOf(neighborhoods);
        fares = List.copyOf(fares);
        path = List.copyOf(path);
    }

    public boolean isActive() {
        return status == RouteStatus.ACTIVA;
    }

    /**
     * Punto del trazado mas cercano a {@code from} y cuanto hay que caminar hasta el
     * (RF-04, ultima milla). Sirve igual para el abordaje (desde el origen) y para la
     * bajada (hacia el destino), y da el mismo resultado online y offline: es
     * geometria sobre el GeoJSON propio, sin llamar a Google.
     *
     * @return vacio si la ruta no tiene trazado cargado todavia. Una ruta asi sigue
     *     siendo buscable; simplemente no se le puede calcular la ultima milla.
     */
    public Optional<BoardingPoint> boardingPointFor(Coordinate from) {
        return PathProjection.nearestPointOn(path, from);
    }

    /**
     * Cuanto hay que viajar en buseta sobre esta ruta entre dos puntos, siguiendo el
     * trazado y no en linea recta.
     *
     * Es el criterio con el que se elige en modo offline: sin red no se puede pedir el
     * tiempo a Google, asi que gana la ruta de menor recorrido. Se mide entre las
     * proyecciones del origen y del destino, asi que no cuenta el trazado que la ruta
     * recorre antes de que el usuario suba ni despues de que se baje.
     *
     * @return vacio si la ruta no tiene trazado cargado
     */
    public OptionalDouble travelDistanceInMeters(Coordinate origin, Coordinate destination) {
        Optional<BoardingPoint> boarding = boardingPointFor(origin);
        Optional<BoardingPoint> dropOff = boardingPointFor(destination);
        if (boarding.isEmpty() || dropOff.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.abs(
                dropOff.get().distanceAlongPathInMeters() - boarding.get().distanceAlongPathInMeters()));
    }

    /**
     * Regla de descarte de RF-04: la ruta deja de ser candidata si obliga a caminar
     * mas de {@link BoardingPoint#MAX_WALKING_DISTANCE_METERS} en cualquiera de las
     * dos puntas.
     *
     * Una ruta sin trazado nunca se descarta por esta via: no hay con que medirla, y
     * sacarla del resultado la haria invisible por un dato que falta, no por una
     * razon de negocio.
     */
    public boolean isWithinWalkingDistance(Coordinate origin, Coordinate destination) {
        return isWithinWalkingDistance(origin) && isWithinWalkingDistance(destination);
    }

    private boolean isWithinWalkingDistance(Coordinate from) {
        return boardingPointFor(from)
                .map(BoardingPoint::isWithinWalkingDistance)
                .orElse(true);
    }

    /**
     * Devuelve la ruta con solo las tarifas vigentes a {@code today}: por cada punto
     * de referencia, la fila con el {@code validFrom} mas reciente que no sea futuro.
     * Las filas futuras se descartan (una tarifa anunciada no rige todavia).
     *
     * El orden es por precio de menor a mayor (RF-06) y no por {@code orderIndex}
     * solo: una ruta puede tener dos series de vigencia cuyos indices se solapan
     * (ver V3), y ordenar solo por indice las mezclaria.
     */
    public Route withCurrentFares(LocalDate today) {
        // HashMap y no Collectors.toMap: el punto de referencia es null en la tarifa urbana.
        Map<String, Fare> latestByPoint = new HashMap<>();
        for (Fare fare : fares) {
            if (fare.validFrom().isAfter(today)) {
                continue;
            }
            latestByPoint.merge(fare.referencePoint(), fare,
                    (a, b) -> b.validFrom().isAfter(a.validFrom()) ? b : a);
        }
        List<Fare> current = latestByPoint.values().stream()
                .sorted(Comparator.comparing(Fare::amount).thenComparingInt(Fare::orderIndex))
                .toList();
        return new Route(id, name, type, status, neighborhoods, current, path);
    }

    /**
     * Fecha de ultima actualizacion del pasaje (RF-06): la mas reciente entre los
     * {@code validFrom} de las tarifas presentes en esta instancia. Llamar despues
     * de {@link #withCurrentFares} para que refleje solo lo vigente, no anuncios
     * futuros.
     */
    public Optional<LocalDate> lastFareUpdate() {
        return fares.stream().map(Fare::validFrom).max(Comparator.naturalOrder());
    }
}
