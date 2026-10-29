package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.List;

/**
 * Fila de {@code route_signs} con sus placas (SCRUM-176). Modelo de persistencia,
 * distinto de {@code domain.model.RouteSign}. Solo lectura.
 *
 * {@code created_at} no se mapea: nada en el dominio lo usa. {@code sense} se mapea
 * como {@code String} crudo y no como enum: el mapper decide si es NULL o una de las
 * dos constantes, y mantener la conversion en un solo lugar evita que
 * {@code @Enumerated} falle de forma oscura ante un valor NULL en la columna.
 */
@Entity
@Table(name = "route_signs")
public class RouteSignJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false, insertable = false, updatable = false)
    private Long routeId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "field_id", nullable = false, length = 16)
    private String fieldId;

    @Column(length = 20)
    private String sense;

    @Column(name = "source_photo", nullable = false, length = 80)
    private String sourcePhoto;

    @Column(name = "evidence_crop", length = 80)
    private String evidenceCrop;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "sign_id", nullable = false, insertable = false, updatable = false)
    @BatchSize(size = 50)
    private List<SignPlateJpaEntity> plates;

    protected RouteSignJpaEntity() {
        // Requerido por JPA.
    }

    public Long getId() {
        return id;
    }

    public Long getRouteId() {
        return routeId;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public String getFieldId() {
        return fieldId;
    }

    public String getSense() {
        return sense;
    }

    public String getSourcePhoto() {
        return sourcePhoto;
    }

    public String getEvidenceCrop() {
        return evidenceCrop;
    }

    public LocalDate getObservedOn() {
        return observedOn;
    }

    public List<SignPlateJpaEntity> getPlates() {
        return plates;
    }
}
