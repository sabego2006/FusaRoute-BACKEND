package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de la tabla {@code fares}. Solo lectura: el catalogo publico no escribe.
 * {@code route_id} lo maneja el {@code @JoinColumn} de {@link RouteJpaEntity} y
 * {@code created_at} no se mapea.
 */
@Entity
@Table(name = "fares")
public class FareJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_point", length = 160)
    private String referencePoint;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    protected FareJpaEntity() {
        // Requerido por JPA.
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReferencePoint() {
        return referencePoint;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }
}
