package com.fusaroute.domain.model;

import java.time.LocalTime;
import java.util.List;

/**
 * Aviso de posible congestion en un recorrido (RF-09): que sectores que el viaje
 * atraviesa estan en su franja de trancon, o a punto de entrar en ella.
 *
 * Se llama "aviso de posible congestion" y no "hay congestion" a proposito: el dato es
 * historico, no una medicion en vivo. El texto que ve el usuario se arma en el frontend
 * y siempre va en condicional ("puede haber congestion"); aqui solo viajan los hechos.
 *
 * @param sectors sectores afectados, en el orden en que se registraron
 */
public record CongestionWarning(List<TrafficSector> sectors) {

    public CongestionWarning {
        sectors = List.copyOf(sectors);
    }

    /** Sin sectores activos no hay nada que avisar. */
    public boolean isEmpty() {
        return sectors.isEmpty();
    }

    /** La hora mas temprana en que arranca alguno de los trancones avisados. */
    public LocalTime earliestStart() {
        return sectors.stream()
                .map(TrafficSector::startTime)
                .min(LocalTime::compareTo)
                .orElseThrow(() -> new IllegalStateException("Un aviso sin sectores no tiene hora de inicio"));
    }
}
