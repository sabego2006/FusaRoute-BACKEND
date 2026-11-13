package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tabla {@code route_suspensions} (V28). {@code route_id} es una columna suelta, como en ride_history: el
 * nombre de la ruta se lee con un JOIN en las consultas nativas. {@code created_at} no se mapea (lo pone la
 * base) y {@code lifted_at} tampoco: solo lo escribe la consulta {@code lift} y solo lo leen las proyecciones.
 */
@Entity
@Table(name = "route_suspensions")
public class RouteSuspensionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    protected RouteSuspensionJpaEntity() {
        // Requerido por JPA.
    }

    public RouteSuspensionJpaEntity(Long routeId, Instant startsAt, Instant endsAt, String reason, Long createdBy) {
        this.routeId = routeId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }
}
