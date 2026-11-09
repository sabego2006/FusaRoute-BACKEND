package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.out.TrafficSectorRepositoryPort;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

/**
 * Arma el aviso de congestion (RF-09) cruzando dos filtros: que el sector este en su
 * franja ahora, y que el recorrido pase por el.
 *
 * El reloj se inyecta en vez de llamar a {@code LocalTime.now()} para que la regla de
 * los 30 minutos previos se pueda probar en sus bordes sin esperar a que den las cinco
 * de la tarde.
 */
public class GetCongestionWarningService implements GetCongestionWarningUseCase {

    private final TrafficSectorRepositoryPort trafficSectorRepository;
    private final Clock clock;

    public GetCongestionWarningService(TrafficSectorRepositoryPort trafficSectorRepository, Clock clock) {
        this.trafficSectorRepository = trafficSectorRepository;
        this.clock = clock;
    }

    @Override
    public CongestionWarning forRoute(List<String> traversedNeighborhoods) {
        return forRoute(traversedNeighborhoods, List.of());
    }

    /**
     * Un sector con circulo (V22) aplica si el trazado recorrido pasa por el; uno sin circulo, por
     * nombre de barrio, como antes. Son criterios excluyentes por sector: el circulo manda.
     */
    @Override
    public CongestionWarning forRoute(List<String> traversedNeighborhoods, List<Coordinate> traversedPath) {
        LocalTime now = LocalTime.now(clock);
        List<TrafficSector> affecting = trafficSectorRepository.findAll().stream()
                .filter(sector -> sector.isWarningActiveAt(now))
                .filter(sector -> sector.hasCircle()
                        ? sector.isCrossedBy(traversedPath)
                        : !traversedNeighborhoods.isEmpty() && sector.covers(traversedNeighborhoods))
                .toList();

        return new CongestionWarning(affecting);
    }
}
