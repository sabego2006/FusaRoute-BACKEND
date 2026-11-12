package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.FeedbackStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * Todos los comentarios con los PENDIENTES PRIMERO (RF-14, SCRUM-98).
     *
     * El orden va con un CASE explicito y no con {@code ORDER BY status DESC}. Lo
     * segundo funcionaria hoy por una casualidad del alfabeto —'PENDING' va despues
     * de 'ANSWERED', asi que DESC los pone primero— y aprovecharia el indice
     * {@code ix_feedback_status_created} sin ordenar nada. Se descarta igual: el dia
     * que el enum tenga un tercer estado, el orden de la pantalla cambiaria solo y
     * sin que nadie toque esta consulta. El CASE dice lo que quiere decir, y cuesta
     * un sort sobre una tabla que en este proyecto tendra cientos de filas, no
     * millones.
     */
    @Query("""
            SELECT f FROM FeedbackJpaEntity f
             ORDER BY CASE WHEN f.status = com.fusaroute.domain.model.FeedbackStatus.PENDING
                           THEN 0 ELSE 1 END,
                      f.createdAt DESC
            """)
    List<FeedbackJpaEntity> findAllPendingFirst();

    /**
     * Solo un estado, del mas reciente al mas antiguo. Esta si entra por el indice
     * {@code ix_feedback_status_created} tal como esta: filtra por la primera columna
     * y lee la segunda en su mismo orden.
     */
    List<FeedbackJpaEntity> findByStatusOrderByCreatedAtDesc(FeedbackStatus status);

    /**
     * Una pagina del feed compartido: todos los comentarios, del mas reciente al mas
     * antiguo, con el id como desempate para que el orden sea total y una fila no
     * salte de pagina entre dos peticiones. Devuelve {@code List} y no {@code Page}:
     * el total lo da {@code count()} del propio repositorio, y asi el conteo es una
     * consulta explicita del puerto y no un efecto lateral de Spring Data.
     *
     * Sin indice propio a proposito: ordena toda la tabla, que en este proyecto tiene
     * cientos de filas (mismo razonamiento que {@link #findAllPendingFirst()}).
     */
    List<FeedbackJpaEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
