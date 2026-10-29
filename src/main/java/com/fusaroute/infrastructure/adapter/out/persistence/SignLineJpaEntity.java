package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de {@code route_sign_lines} (SCRUM-176). Solo lectura. {@code line_role}
 * no se mapea todavia: la semilla la deja NULL (ver V11) y nada en el dominio la usa.
 */
@Entity
@Table(name = "route_sign_lines")
public class SignLineJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "line_text", nullable = false, length = 60)
    private String lineText;

    @Column(length = 7)
    private String background;

    @Column(length = 7)
    private String foreground;

    protected SignLineJpaEntity() {
        // Requerido por JPA.
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public String getLineText() {
        return lineText;
    }

    public String getBackground() {
        return background;
    }

    public String getForeground() {
        return foreground;
    }
}
