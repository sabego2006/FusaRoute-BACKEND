package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.SearchRecord;
import com.fusaroute.domain.port.in.GetSearchHistoryUseCase;
import com.fusaroute.domain.port.in.RecordSearchUseCase;
import com.fusaroute.domain.port.out.SearchHistoryRepositoryPort;

import java.time.Clock;
import java.util.List;

/**
 * Historial de busquedas (RF-07, SCRUM-20).
 *
 * Los dos casos de uso viven en la misma clase porque comparten la misma regla
 * —el tope de {@link SearchRecord#MAX_PER_USER}— y separarlos obligaria a
 * repetirla en dos sitios.
 *
 * El {@link Clock} se inyecta (bean ya existente en {@code UseCaseConfig}, fijado
 * a America/Bogota) para que los tests fijen el instante y puedan comprobar el
 * orden del historial sin depender de la hora real.
 */
public class SearchHistoryService implements RecordSearchUseCase, GetSearchHistoryUseCase {

    private final SearchHistoryRepositoryPort repository;
    private final Clock clock;

    public SearchHistoryService(SearchHistoryRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void record(Long userId, String origin, String destination) {
        repository.save(SearchRecord.of(userId, origin, destination, clock.instant()));
        // Se recorta despues de guardar, no antes: si se borrara primero y el
        // guardado fallara, el usuario perderia una busqueda sin ganar ninguna.
        repository.deleteOlderBeyond(userId, SearchRecord.MAX_PER_USER);
    }

    @Override
    public List<SearchRecord> historyOf(Long userId) {
        return repository.findRecentByUser(userId, SearchRecord.MAX_PER_USER);
    }
}
