package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.FavoriteDestination;
import com.fusaroute.domain.port.in.ClearFavoriteDestinationUseCase;
import com.fusaroute.domain.port.in.GetFavoriteDestinationUseCase;
import com.fusaroute.domain.port.in.SetFavoriteDestinationUseCase;
import com.fusaroute.domain.port.out.FavoriteDestinationRepositoryPort;

import java.time.Clock;
import java.util.Optional;

/**
 * Destino favorito del usuario (RF-10, SCRUM-21).
 *
 * Los tres casos de uso viven en la misma clase, igual que en
 * {@link SearchHistoryService}: operan sobre el mismo unico registro por cuenta y
 * separarlos solo repartiria tres lineas en tres archivos.
 *
 * El {@link Clock} se inyecta (bean de {@code UseCaseConfig}, fijado a America/Bogota)
 * para que los tests fijen el instante sin depender de la hora real.
 */
public class FavoriteDestinationService
        implements SetFavoriteDestinationUseCase, GetFavoriteDestinationUseCase, ClearFavoriteDestinationUseCase {

    private final FavoriteDestinationRepositoryPort repository;
    private final Clock clock;

    public FavoriteDestinationService(FavoriteDestinationRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public FavoriteDestination set(Long userId, String destination) {
        // No se consulta si ya habia uno: el guardado reemplaza, porque la llave
        // primaria de la tabla es el usuario. Preguntar antes seria una ida a la base
        // que no cambia lo que se hace despues.
        return repository.save(new FavoriteDestination(userId, destination, clock.instant()));
    }

    @Override
    public Optional<FavoriteDestination> of(Long userId) {
        return repository.findByUser(userId);
    }

    @Override
    public void clear(Long userId) {
        repository.deleteByUser(userId);
    }
}
