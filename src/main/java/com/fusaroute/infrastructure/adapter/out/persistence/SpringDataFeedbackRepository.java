package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SpringDataFeedbackRepository extends JpaRepository<FeedbackJpaEntity, Long> {

    /**
     * Comentarios del usuario, del mas reciente al mas antiguo. Ese orden
     * ({@code user_id}, {@code created_at DESC}) es el del indice
     * {@code ix_feedback_user_created}, asi que Postgres los lee sin ordenar nada
     * despues. Si ese indice cambia de nombre o de columnas, actualizar aqui tambien.
     *
     * Sin {@code Pageable}, a diferencia del historial de busquedas: los comentarios
     * no tienen tope de 6, se devuelven todos.
     */
    List<FeedbackJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Cuantos comentarios hay en un estado. Lo usa el conteo de pendientes, que es la
     * metrica 4 del panel de RF-13, y lo sostiene la primera columna del indice
     * {@code ix_feedback_status_created}: el conteo no recorre la tabla entera.
     */
    long countByStatus(FeedbackStatus status);

    /**
     * Comentarios de un usuario respondidos despues de {@code since} (SCRUM-101).
     *
     * Filtra ademas por estado y no solo por la fecha, aunque el {@code CHECK} de la
     * V19 ya garantice que {@code answered_at} solo esta lleno cuando el estado es
     * ANSWERED. Es redundante a proposito: deja la consulta legible sin tener que ir
     * a leer la restriccion de la base para entender que cuenta.
     */
    long countByUserIdAndStatusAndAnsweredAtAfter(Long userId, FeedbackStatus status, Instant since);
}
