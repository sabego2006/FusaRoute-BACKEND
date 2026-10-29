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

import java.util.List;

/**
 * Fila de {@code route_sign_plates} con sus lineas (SCRUM-176). Solo lectura.
 * {@code @BatchSize} por la misma razon que en {@link RouteJpaEntity}: lineas LAZY
 * de varias placas a la vez no se pueden traer con JOIN FETCH sin duplicar filas.
 */
@Entity
@Table(name = "route_sign_plates")
public class SignPlateJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 20)
    private String template;

    @Column(length = 10)
    private String code;

    @Column(length = 7)
    private String background;

    @Column(length = 7)
    private String foreground;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "plate_id", nullable = false, insertable = false, updatable = false)
    @BatchSize(size = 50)
    private List<SignLineJpaEntity> lines;

    protected SignPlateJpaEntity() {
        // Requerido por JPA.
    }

    public int getPosition() {
        return position;
    }

    public String getTemplate() {
        return template;
    }

    public String getCode() {
        return code;
    }

    public String getBackground() {
        return background;
    }

    public String getForeground() {
        return foreground;
    }

    public List<SignLineJpaEntity> getLines() {
        return lines;
    }
}
