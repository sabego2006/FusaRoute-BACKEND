package com.fusaroute.domain.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ruta de buseta: un recorrido directo de un solo tramo, con sus barrios en orden
 * de recorrido y sus tarifas. Ida y vuelta son dos rutas independientes.
 */
public record Route(Long id, String name, RouteType type, RouteStatus status,
                    List<String> neighborhoods, List<Fare> fares) {

    public Route {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        neighborhoods = List.copyOf(neighborhoods);
        fares = List.copyOf(fares);
    }

    public boolean isActive() {
        return status == RouteStatus.ACTIVA;
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
        return new Route(id, name, type, status, neighborhoods, current);
    }
}
