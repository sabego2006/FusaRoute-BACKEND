package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;

import java.util.List;
import java.util.Map;

/**
 * Traduce la entidad JPA a la ruta de dominio. Solo en un sentido: el catalogo es de
 * solo lectura. Vivir aqui mantiene {@code domain.model.Route} libre de JPA.
 */
final class RoutePersistenceMapper {

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
                fares);
    }
}
