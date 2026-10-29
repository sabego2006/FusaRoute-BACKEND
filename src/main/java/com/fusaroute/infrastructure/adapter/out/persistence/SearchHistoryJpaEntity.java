package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Fila de {@code search_history} (RF-07, SCRUM-20). Modelo de persistencia,
 * distinto de {@code domain.model.SearchRecord}.
 *
 * {@code user_id} se mapea como columna suelta y no como {@code @ManyToOne} a
 * {@link UserJpaEntity}: el historial solo necesita el id para filtrar, y una
 * asociacion traeria el usuario entero en cada lectura sin que nadie lo use.
 */
@Entity
@Table(name = "search_history")
public class SearchHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String origin;

    @Column(nullable = false, length = 120)
    private String destination;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SearchHistoryJpaEntity() {
        // Requerido por JPA.
    }

    public SearchHistoryJpaEntity(Long userId, String origin, String destination, Instant createdAt) {
        this.userId = userId;
        this.origin = origin;
        this.destination = destination;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
