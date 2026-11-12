package com.fusaroute.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Ruta tal como la ve el administrador: en cualquier estado y con la fecha de su ultimo
 * cambio. {@code pathGeoJson} es el LineString crudo (o nulo): el dominio no lo interpreta.
 */
public record AdminRoute(Long id, String name, RouteType type, RouteStatus status, String signFile,
                         String pathGeoJson, List<String> neighborhoods, List<Fare> fares, Instant updatedAt) {

    public AdminRoute {
        neighborhoods = List.copyOf(neighborhoods);
        fares = List.copyOf(fares);
    }

    public boolean hasPath() {
        return pathGeoJson != null;
    }

    /** Misma copia pero solo con las tarifas vigentes a {@code today} (misma regla que el catalogo publico). */
    public AdminRoute withCurrentFares(LocalDate today) {
        List<Fare> current = new Route(id, name, signFile, type, status, neighborhoods, fares, List.of())
                .withCurrentFares(today).fares();
        return new AdminRoute(id, name, type, status, signFile, pathGeoJson, neighborhoods, current, updatedAt);
    }

    /** Tarifa de referencia para el listado: la menor de las vigentes de la ruta. */
    public Optional<BigDecimal> currentFare() {
        return fares.stream().map(Fare::amount).min(BigDecimal::compareTo);
    }
}
