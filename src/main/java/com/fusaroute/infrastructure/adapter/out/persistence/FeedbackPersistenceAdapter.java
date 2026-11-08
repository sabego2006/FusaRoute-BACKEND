package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;
import com.fusaroute.domain.port.out.FeedbackRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de salida de {@link FeedbackRepositoryPort} sobre Spring Data
 * (RF-08 y RF-14, SCRUM-22 y SCRUM-23).
 */
@Component
public class FeedbackPersistenceAdapter implements FeedbackRepositoryPort {

    private final SpringDataFeedbackRepository repository;

    public FeedbackPersistenceAdapter(SpringDataFeedbackRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Feedback save(Feedback feedback) {
        // El mismo save sirve para insertar y para actualizar: con id nulo Spring Data
        // hace persist y con id hace merge. Por eso editar y responder no necesitan un
        // metodo aparte en el puerto.
        FeedbackJpaEntity saved = repository.save(toEntity(feedback));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Feedback> findById(Long feedbackId) {
        return repository.findById(feedbackId).map(FeedbackPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Feedback> findByUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(FeedbackPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long feedbackId) {
        // El caso de uso ya comprobo que el comentario existe, es del usuario y sigue
        // pendiente, asi que aqui no hay nada mas que validar.
        repository.deleteById(feedbackId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return repository.countByStatus(FeedbackStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAnsweredSince(Long userId, Instant since) {
        return repository.countByUserIdAndStatusAndAnsweredAtAfter(
                userId, FeedbackStatus.ANSWERED, since);
    }

    private static FeedbackJpaEntity toEntity(Feedback feedback) {
        return new FeedbackJpaEntity(
                feedback.getId(),
                feedback.getUserId(),
                feedback.getAuthorName(),
                feedback.getText(),
                feedback.getStatus(),
                feedback.getReply(),
                feedback.getAnsweredAt(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }

    private static Feedback toDomain(FeedbackJpaEntity entity) {
        return new Feedback(
                entity.getId(),
                entity.getUserId(),
                entity.getAuthorName(),
                entity.getText(),
                entity.getStatus(),
                entity.getReply(),
                entity.getAnsweredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
