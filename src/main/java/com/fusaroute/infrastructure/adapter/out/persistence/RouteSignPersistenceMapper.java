package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.RouteSign;
import com.fusaroute.domain.model.SignLine;
import com.fusaroute.domain.model.SignPlate;
import com.fusaroute.domain.model.SignSense;
import com.fusaroute.domain.model.SignTemplate;

import java.util.Comparator;

/**
 * Traduce las entidades JPA de letrero a los tipos de dominio (SCRUM-176). Solo en un
 * sentido: la lectura es de solo lectura, igual que {@code RoutePersistenceMapper}.
 */
final class RouteSignPersistenceMapper {

    private RouteSignPersistenceMapper() {
    }

    static RouteSign toDomain(RouteSignJpaEntity entity) {
        return new RouteSign(
                entity.getFieldId(),
                entity.getSense() == null ? null : SignSense.valueOf(entity.getSense()),
                entity.getSourcePhoto(),
                entity.getEvidenceCrop(),
                entity.getObservedOn(),
                entity.getPlates().stream()
                        .sorted(Comparator.comparingInt(SignPlateJpaEntity::getPosition))
                        .map(RouteSignPersistenceMapper::toDomain)
                        .toList());
    }

    private static SignPlate toDomain(SignPlateJpaEntity entity) {
        return new SignPlate(
                SignTemplate.valueOf(entity.getTemplate()),
                entity.getCode(),
                entity.getLines().stream()
                        .sorted(Comparator.comparingInt(SignLineJpaEntity::getOrderIndex))
                        .map(RouteSignPersistenceMapper::toDomain)
                        .toList());
    }

    private static SignLine toDomain(SignLineJpaEntity entity) {
        return new SignLine(entity.getLineText(), entity.getBackground(), entity.getForeground());
    }
}
