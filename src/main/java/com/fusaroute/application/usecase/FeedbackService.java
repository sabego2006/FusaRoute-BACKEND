package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.FeedbackNotFoundException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.CountPendingFeedbackUseCase;
import com.fusaroute.domain.port.in.CountUnreadRepliesUseCase;
import com.fusaroute.domain.port.in.DeleteFeedbackUseCase;
import com.fusaroute.domain.port.in.EditFeedbackUseCase;
import com.fusaroute.domain.port.in.ListMyFeedbackUseCase;
import com.fusaroute.domain.port.in.SubmitFeedbackUseCase;
import com.fusaroute.domain.port.out.FeedbackRepositoryPort;
import com.fusaroute.domain.port.out.ForbiddenWordsPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Caja de comentarios del usuario (RF-08, SCRUM-22).
 *
 * Sin anotaciones de Spring: recibe sus puertos por constructor y se prueba con
 * mocks sin levantar el contexto. El bean lo arma {@code UseCaseConfig}.
 *
 * Los casos de uso del usuario viven en la misma clase, igual que
 * {@link FavoriteDestinationService} y {@link SearchHistoryService}: operan sobre la
 * misma tabla y los mismos puertos, y separarlos repartiria tres lineas por archivo.
 * Responder un comentario NO esta aqui: es del administrador (RF-14, SCRUM-23) y
 * tiene su propio caso de uso, porque su autorizacion es distinta.
 *
 * <p>Este servicio no valida nada por su cuenta. Las reglas del texto, la autoria y
 * el bloqueo tras la respuesta estan en {@link Feedback}; aqui solo se orquesta:
 * buscar, delegar en el dominio, guardar. Es lo que hace que las reglas se puedan
 * probar sin mocks y lo que mantiene la cobertura de JaCoCo donde significa algo.
 */
public class FeedbackService implements SubmitFeedbackUseCase, ListMyFeedbackUseCase,
        EditFeedbackUseCase, DeleteFeedbackUseCase, CountPendingFeedbackUseCase,
        CountUnreadRepliesUseCase {

    private final FeedbackRepositoryPort repository;
    private final UserRepositoryPort userRepository;
    private final ForbiddenWordsPort forbiddenWords;
    private final Clock clock;

    public FeedbackService(FeedbackRepositoryPort repository,
                           UserRepositoryPort userRepository,
                           ForbiddenWordsPort forbiddenWords,
                           Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.forbiddenWords = forbiddenWords;
        this.clock = clock;
    }

    @Override
    public Feedback submit(Long userId, String text) {
        // El nombre del autor se lee del perfil y no del cuerpo de la peticion: es la
        // unica forma de que "cada comentario muestra el nombre de usuario de quien lo
        // escribio" no se pueda falsificar desde el cliente.
        User author = requireUser(userId);
        Feedback feedback = Feedback.create(
                userId, author.getName(), text, forbiddenWords.words(), clock.instant());
        return repository.save(feedback);
    }

    @Override
    public List<Feedback> myFeedback(Long userId) {
        return repository.findByUser(userId);
    }

    @Override
    public Feedback edit(Long userId, Long feedbackId, String newText) {
        Feedback feedback = requireOwnFeedback(userId, feedbackId);
        // editedTo vuelve a exigir que siga pendiente y revalida el texto: editar no
        // es menos escritura que crear.
        return repository.save(feedback.editedTo(newText, forbiddenWords.words(), clock.instant()));
    }

    @Override
    public void delete(Long userId, Long feedbackId) {
        Feedback feedback = requireOwnFeedback(userId, feedbackId);
        feedback.requirePending();
        repository.delete(feedbackId);
    }

    @Override
    public long countPending() {
        return repository.countPending();
    }

    @Override
    public long unreadReplies(Long userId) {
        Instant since = requireUser(userId).getPreviousLoginAt();
        if (since == null) {
            // Primer ingreso del usuario: no hay un "antes" contra el que comparar.
            // Devolver su historial completo marcaria como nuevas respuestas que ya
            // habia leido en la misma sesion en que las recibio.
            return 0L;
        }
        return repository.countAnsweredSince(userId, since);
    }

    /**
     * Carga el comentario y comprueba que sea del usuario. El orden importa: primero
     * autoria y despues estado, para que un tercero no pueda averiguar si el
     * comentario de otro ya fue respondido.
     */
    private Feedback requireOwnFeedback(Long userId, Long feedbackId) {
        Feedback feedback = repository.findById(feedbackId)
                .orElseThrow(() -> new FeedbackNotFoundException("El comentario no existe"));
        feedback.requireAuthoredBy(userId);
        return feedback;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }
}
