package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Tabla {@code ride_history} (V25). user_id y route_id son columnas sueltas, como en search_history. */
@Entity
@Table(name = "ride_history")
public class RideHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "destination_label", nullable = false, length = 120)
    private String destinationLabel;

    @Column(name = "destination_place_id")
    private Long destinationPlaceId;

    @Column(name = "destination_lat", nullable = false)
    private double destinationLat;

    @Column(name = "destination_lng", nullable = false)
    private double destinationLng;

    @Column(name = "origin_label", nullable = false, length = 120)
    private String originLabel;

    @Column(name = "boarded_at", nullable = false)
    private Instant boardedAt;

    protected RideHistoryJpaEntity() {
        // Requerido por JPA.
    }

    @SuppressWarnings("java:S107") // una columna por parametro: es la fila tal cual.
    public RideHistoryJpaEntity(Long userId, Long routeId, String destinationLabel, Long destinationPlaceId,
                                double destinationLat, double destinationLng, String originLabel,
                                Instant boardedAt) {
        this.userId = userId;
        this.routeId = routeId;
        this.destinationLabel = destinationLabel;
        this.destinationPlaceId = destinationPlaceId;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.originLabel = originLabel;
        this.boardedAt = boardedAt;
    }

    public Long getId() {
        return id;
    }
}
