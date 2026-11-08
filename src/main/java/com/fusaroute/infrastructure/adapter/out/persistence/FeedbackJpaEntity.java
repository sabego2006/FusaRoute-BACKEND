package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.FeedbackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Fila de {@code feedback} (RF-08 y RF-14, V19). Modelo de persistencia, distinto de
 * {@code domain.model.Feedback}.
 *
 * {@code user_id} se mapea como columna suelta y no como {@code @ManyToOne} a
 * {@link UserJpaEntity}, por la misma razon que en {@link SearchHistoryJpaEntity} y
 * {@link FavoriteDestinationJpaEntity}: el listado solo necesita el id para filtrar y
 * una asociacion traeria el usuario entero en cada lectura.
 *
 * {@code author_name} es una copia del nombre al momento de escribir y no una lectura
 * en vivo por la FK. Ese es justamente el motivo por el que la asociacion no hace
 * falta: el nombre que el comentario debe mostrar ya esta en esta fila.
 *
 * El estado va con {@code @Enumerated(STRING)} igual que {@code users.role}: la
 * columna guarda 'PENDING'/'ANSWERED' legibles, que es lo que el {@code CHECK} de la
 * V19 valida y lo que se ve al consultar la tabla a mano en DBeaver. Con ORDINAL,
 * reordenar el enum cambiaria el significado de las filas ya guardadas.
 *
 * {@code created_at} y {@code updated_at} SI se mapean, a diferencia de
 * {@link UserJpaEntity}, donde los llena el default de la base: aqui el dominio los
 * calcula con el {@code Clock} inyectado, porque {@code updated_at} cambia al editar
 * y {@code answered_at} tiene que poder fijarse en un test sin depender de la hora
 * real del servidor.
 */
@Entity
@Table(name = "feedback")
public class FeedbackJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    @Column(nullable = false, length = 100)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status;

    @Column(length = 500)
    private String reply;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeedbackJpaEntity() {
        // Requerido por JPA.
    }

    public FeedbackJpaEntity(Long id, Long userId, String authorName, String text,
                             FeedbackStatus status, String reply, Instant answeredAt,
                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.authorName = authorName;
        this.text = text;
        this.status = status;
        this.reply = reply;
        this.answeredAt = answeredAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getText() {
        return text;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public String getReply() {
        return reply;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
