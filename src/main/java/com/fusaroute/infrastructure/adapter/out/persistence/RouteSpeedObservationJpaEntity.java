package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Fila de {@code route_speed_observations} (V22): la velocidad que Google midio en una busqueda. */
@Entity
@Table(name = "route_speed_observations")
public class RouteSpeedObservationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "observed_kmh", nullable = false, precision = 6, scale = 2)
    private BigDecimal observedKmh;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected RouteSpeedObservationJpaEntity() {
        // Requerido por JPA.
    }

    public RouteSpeedObservationJpaEntity(Long routeId, BigDecimal observedKmh, Instant observedAt) {
        this.routeId = routeId;
        this.observedKmh = observedKmh;
        this.observedAt = observedAt;
    }

    public Long getRouteId() {
        return routeId;
    }

    public BigDecimal getObservedKmh() {
        return observedKmh;
    }
}
