package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.port.out.RouteSpeedPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de salida de {@link RouteSpeedPort}. Lee las {@link #WINDOW} observaciones mas recientes
 * de todas las rutas y reparte las ultimas N de cada una: con el tope de ~150 busquedas diarias a
 * Google, esa ventana cubre semanas de historia sin traer toda la tabla.
 */
@Component
public class RouteSpeedPersistenceAdapter implements RouteSpeedPort {

    private static final int WINDOW = 5_000;

    private final SpringDataRouteSpeedRepository repository;

    public RouteSpeedPersistenceAdapter(SpringDataRouteSpeedRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<Double>> recentObservations(int perRoute) {
        Map<Long, List<Double>> byRoute = new HashMap<>();
        for (RouteSpeedObservationJpaEntity row : repository.findMostRecent(PageRequest.of(0, WINDOW))) {
            List<Double> list = byRoute.computeIfAbsent(row.getRouteId(), id -> new ArrayList<>());
            if (list.size() < perRoute) {
                list.add(row.getObservedKmh().doubleValue());
            }
        }
        return byRoute;
    }

    @Override
    @Transactional
    public void record(long routeId, double observedKmh) {
        repository.save(new RouteSpeedObservationJpaEntity(routeId,
                BigDecimal.valueOf(observedKmh).setScale(2, RoundingMode.HALF_UP), Instant.now()));
    }
}
