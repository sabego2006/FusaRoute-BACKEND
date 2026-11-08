package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.FeedbackNotFoundException;
import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;
import com.fusaroute.domain.port.in.ListAllFeedbackUseCase;
import com.fusaroute.domain.port.in.ReplyToFeedbackUseCase;
import com.fusaroute.domain.port.out.FeedbackRepositoryPort;

import java.time.Clock;
import java.util.List;

/**
 * Comentarios desde el lado del administrador (RF-14, SCRUM-23).
 *
 * Esta aparte de {@link FeedbackService} a proposito, aunque comparta repositorio.
 * La razon no es tamano: es que su autorizacion es distinta. Los casos de uso de
 * {@code FeedbackService} se validan contra "soy el autor" y los de aqui contra
 * "tengo rol de administrador", y mezclarlos en una clase haria que leer un metodo no
 * alcance para saber a quien le pertenece. Tambien deja ver en el import de un
 * controlador de que lado esta.
 *
 * <p>Ninguno de los dos metodos filtra por usuario: el administrador ve y responde
 * todos los comentarios. Lo que impide que un USER llegue aqui no esta en esta clase
 * sino en {@code SecurityConfig} (SCRUM-104), que es donde debe estar — la regla del
 * repo dice que la verificacion de rol va en la capa de seguridad y no dispersa en
 * los casos de uso.
 */
public class AdminFeedbackService implements ListAllFeedbackUseCase, ReplyToFeedbackUseCase {

    private final FeedbackRepositoryPort repository;
    private final Clock clock;

    public AdminFeedbackService(FeedbackRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public List<Feedback> allFeedbackPendingFirst() {
        return repository.findAllPendingFirst();
    }

    @Override
    public List<Feedback> allFeedbackWithStatus(FeedbackStatus status) {
        return repository.findAllByStatus(status);
    }

    @Override
    public Feedback reply(Long feedbackId, String reply) {
        Feedback feedback = repository.findById(feedbackId)
                .orElseThrow(() -> new FeedbackNotFoundException("El comentario no existe"));
        // answeredWith sirve igual para la primera respuesta y para corregirla: en los
        // dos casos queda UNA respuesta y answered_at pasa a ahora. No se comprueba si
        // ya estaba respondido, porque corregir es justo lo que el criterio permite.
        return repository.save(feedback.answeredWith(reply, clock.instant()));
    }
}
