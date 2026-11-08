package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.Feedback;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de los comentarios (RF-08 y RF-14, SCRUM-22 y SCRUM-23).
 */
public interface FeedbackRepositoryPort {

    /**
     * Inserta el comentario nuevo o guarda los cambios de uno existente, segun
     * traiga id o no. Devuelve la version con el id asignado.
     */
    Feedback save(Feedback feedback);

    /**
     * Vacio si no existe. NO filtra por usuario: la autoria la comprueba el dominio
     * con {@code Feedback.requireAuthoredBy}, para que la regla viva en un solo
     * lugar y el administrador pueda usar el mismo metodo sin un segundo camino.
     */
    Optional<Feedback> findById(Long feedbackId);

    /** Comentarios del usuario, del mas reciente al mas antiguo. */
    List<Feedback> findByUser(Long userId);

    void delete(Long feedbackId);

    /** Metrica 4 del panel de RF-13: comentarios en estado pendiente. */
    long countPending();

    /**
     * Comentarios del usuario respondidos despues de {@code since} (SCRUM-101).
     *
     * El filtro va en la consulta y no en memoria porque es un conteo: traer los
     * comentarios para contarlos seria leer filas que nadie va a mostrar.
     */
    long countAnsweredSince(Long userId, Instant since);
}
