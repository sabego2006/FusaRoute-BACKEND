package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Fila de {@code favorite_destination} (RF-10, SCRUM-21). Modelo de persistencia,
 * distinto de {@code domain.model.FavoriteDestination}.
 *
 * La llave primaria es {@code user_id} y no un id propio autogenerado: el criterio de
 * aceptacion dice que hay un solo favorito activo por usuario, y ponerlo como PK hace
 * que la base lo garantice en vez de confiar en que el codigo no inserte dos.
 *
 * Eso tambien es lo que convierte {@code save} en un reemplazo: con el id asignado a
 * mano, Spring Data hace {@code merge}, que inserta si no existe y actualiza si ya
 * estaba. No hace falta un upsert escrito a mano.
 *
 * {@code user_id} se mapea como columna suelta y no como {@code @OneToOne} a
 * {@link UserJpaEntity}, por la misma razon que en {@link SearchHistoryJpaEntity}: solo
 * se necesita el id para filtrar.
 */
@Entity
@Table(name = "favorite_destination")
public class FavoriteDestinationJpaEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 120)
    private String destination;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FavoriteDestinationJpaEntity() {
        // Requerido por JPA.
    }

    public FavoriteDestinationJpaEntity(Long userId, String destination, Instant createdAt) {
        this.userId = userId;
        this.destination = destination;
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDestination() {
        return destination;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
