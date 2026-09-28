package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.out.RouteRepositoryPort;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Listado publico de rutas activas. Sin anotaciones de Spring: el bean lo arma
 * {@code UseCaseConfig}. El {@link Clock} entra por constructor para poder fijar
 * "hoy" en los tests de tarifa vigente.
 */
public class ListActiveRoutesService implements ListActiveRoutesUseCase {

    private final RouteRepositoryPort routeRepository;
    private final Clock clock;

    public ListActiveRoutesService(RouteRepositoryPort routeRepository, Clock clock) {
        this.routeRepository = routeRepository;
        this.clock = clock;
    }

    @Override
    public List<Route> listActive() {
        LocalDate today = LocalDate.now(clock);
        // Collator y no String.compareTo: una ruta que empiece con tilde debe ordenar junto a su letra.
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es-CO"));
        return routeRepository.findAll().stream()
                .filter(Route::isActive)
                .map(route -> route.withCurrentFares(today))
                .sorted((a, b) -> collator.compare(a.name(), b.name()))
                .toList();
    }
}
