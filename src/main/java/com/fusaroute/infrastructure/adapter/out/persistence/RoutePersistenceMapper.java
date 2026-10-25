package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Traduce la entidad JPA a la ruta de dominio. Solo en un sentido: el catalogo es de
 * solo lectura. Vivir aqui mantiene {@code domain.model.Route} libre de JPA.
 */
final class RoutePersistenceMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private RoutePersistenceMapper() {
    }

    static Route toDomain(RouteJpaEntity entity) {
        List<Fare> fares = entity.getFares().stream()
                .map(f -> new Fare(f.getReferencePoint(), f.getAmount(), f.getOrderIndex(), f.getValidFrom()))
                .toList();
        // Ordenar por indice de recorrido; los huecos de la semilla simplemente no aparecen.
        List<String> neighborhoods = entity.getNeighborhoods().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
        return new Route(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getStatus(),
                neighborhoods,
                fares,
                parsePath(entity.getPathGeoJson()));
    }

    /**
     * Parsea un LineString de GeoJSON ({@code {"type":"LineString","coordinates":[[lng,lat],...]}})
     * a la secuencia de {@link Coordinate} del dominio. NULL en las rutas sin trazado
     * todavia (SCRUM-166) devuelve una lista vacia, no un error: una ruta sin trazado
     * sigue siendo buscable y listable, solo no participa del calculo por distancia.
     *
     * GeoJSON ordena cada punto como [longitud, latitud]; Coordinate es
     * (latitud, longitud) — invertir el orden aqui es a proposito, no un bug.
     */
    private static List<Coordinate> parsePath(String pathGeoJson) {
        if (pathGeoJson == null) {
            return List.of();
        }
        JsonNode coordinates;
        try {
            coordinates = JSON.readTree(pathGeoJson).path("coordinates");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("path_geojson invalido en la base de datos", e);
        }
        List<Coordinate> path = new ArrayList<>();
        for (JsonNode point : coordinates) {
            path.add(new Coordinate(point.get(1).asDouble(), point.get(0).asDouble()));
        }
        return List.copyOf(path);
    }
}
