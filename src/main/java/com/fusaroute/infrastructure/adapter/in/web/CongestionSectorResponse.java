package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.TrafficSector;

import java.time.LocalTime;
import java.util.List;

/**
 * Sector congestionado que afecta a un recorrido (RF-09).
 *
 * Viajan los hechos —que sector y en que franja— y no una frase armada: el texto que
 * ve el usuario lo compone el frontend, y siempre en condicional ("puede haber
 * congestion"), porque el dato es historico y no una medicion en vivo.
 *
 * @param name nombre del sector, p. ej. "Centro - Carrera 1"
 * @param startsAt hora en que historicamente arranca el trancon
 * @param endsAt hora en que afloja
 */
public record CongestionSectorResponse(String name, LocalTime startsAt, LocalTime endsAt) {

    /** Lista vacia cuando no hay ningun sector activo; no hace falta distinguir null. */
    static List<CongestionSectorResponse> from(CongestionWarning warning) {
        return warning.sectors().stream().map(CongestionSectorResponse::from).toList();
    }

    private static CongestionSectorResponse from(TrafficSector sector) {
        return new CongestionSectorResponse(sector.name(), sector.startTime(), sector.endTime());
    }
}
