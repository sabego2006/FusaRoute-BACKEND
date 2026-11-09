package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.UsageEvent;

import java.time.Instant;
import java.util.List;

/** Lectura de {@code search_events} para el panel del administrador (SCRUM-186). */
public interface UsageEventQueryPort {

    /**
     * Los eventos mas recientes primero.
     *
     * @param since solo filas con {@code ts >= since}, o {@code null} para sin limite.
     * @param limit maximo de filas; el llamador ya lo acoto.
     */
    List<UsageEvent> findRecent(Instant since, int limit);
}
