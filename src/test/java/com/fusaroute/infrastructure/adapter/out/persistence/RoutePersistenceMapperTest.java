package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePersistenceMapperTest {

    private static RouteJpaEntity entity(Map<Integer, String> neighborhoods, List<FareJpaEntity> fares) {
        RouteJpaEntity entity = new RouteJpaEntity();
        ReflectionTestUtils.setField(entity, "id", 1L);
        ReflectionTestUtils.setField(entity, "name", "Llano Largo - La Clarita (\"100\")");
        ReflectionTestUtils.setField(entity, "type", RouteType.URBANA);
        ReflectionTestUtils.setField(entity, "status", RouteStatus.ACTIVA);
        ReflectionTestUtils.setField(entity, "neighborhoods", neighborhoods);
        ReflectionTestUtils.setField(entity, "fares", fares);
        return entity;
    }

    @Test
    void ordena_los_barrios_por_indice_y_tolera_huecos_sin_nulls() {
        // Caso real de la semilla V2: la ruta "100" no tiene indice 2 (tramo sin datos).
        Map<Integer, String> neighborhoods = new HashMap<>();
        neighborhoods.put(3, "Centro");
        neighborhoods.put(0, "Llano Verde");
        neighborhoods.put(1, "Llano Largo");
        neighborhoods.put(4, "La Clarita");

        Route route = RoutePersistenceMapper.toDomain(entity(neighborhoods, List.of()));

        assertThat(route.neighborhoods()).containsExactly("Llano Verde", "Llano Largo", "Centro", "La Clarita");
    }

    @Test
    void traduce_tarifas_y_campos_de_la_ruta() {
        FareJpaEntity fare = new FareJpaEntity();
        ReflectionTestUtils.setField(fare, "amount", new BigDecimal("2600.00"));
        ReflectionTestUtils.setField(fare, "orderIndex", 0);
        ReflectionTestUtils.setField(fare, "validFrom", LocalDate.of(2026, 2, 5));

        Route route = RoutePersistenceMapper.toDomain(entity(Map.of(0, "Centro"), List.of(fare)));

        assertThat(route.id()).isEqualTo(1L);
        assertThat(route.type()).isEqualTo(RouteType.URBANA);
        assertThat(route.isActive()).isTrue();
        assertThat(route.fares()).hasSize(1);
        assertThat(route.fares().get(0).referencePoint()).isNull();
        assertThat(route.fares().get(0).amount()).isEqualByComparingTo("2600");
        assertThat(route.fares().get(0).validFrom()).isEqualTo(LocalDate.of(2026, 2, 5));
    }
}
