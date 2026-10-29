package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.SearchRecord;
import com.fusaroute.domain.port.out.SearchHistoryRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adaptador de salida de {@link SearchHistoryRepositoryPort} sobre Spring Data
 * (RF-07, SCRUM-20).
 */
@Component
public class SearchHistoryPersistenceAdapter implements SearchHistoryRepositoryPort {

    private final SpringDataSearchHistoryRepository repository;

    public SearchHistoryPersistenceAdapter(SpringDataSearchHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public SearchRecord save(SearchRecord record) {
        SearchHistoryJpaEntity saved = repository.save(new SearchHistoryJpaEntity(
                record.userId(), record.origin(), record.destination(), record.searchedAt()));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchRecord> findRecentByUser(Long userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream().map(SearchHistoryPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public int deleteOlderBeyond(Long userId, int keep) {
        return repository.deleteOlderBeyond(userId, keep);
    }

    private static SearchRecord toDomain(SearchHistoryJpaEntity entity) {
        return new SearchRecord(entity.getId(), entity.getUserId(),
                entity.getOrigin(), entity.getDestination(), entity.getCreatedAt());
    }
}
