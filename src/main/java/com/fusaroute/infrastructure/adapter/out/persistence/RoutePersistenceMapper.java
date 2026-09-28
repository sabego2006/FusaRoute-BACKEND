package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;

import java.util.List;

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
        return new Route(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getStatus(),
                List.copyOf(entity.getNeighborhoods()),
                fares);
    }
}
