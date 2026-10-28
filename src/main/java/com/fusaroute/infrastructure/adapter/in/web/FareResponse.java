package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Fare;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tarifa vigente de una ruta. {@code referencePoint} es {@code null} en la tarifa
 * unica de una ruta urbana.
 */
public record FareResponse(String referencePoint, BigDecimal amount, LocalDate validFrom) {

    static FareResponse from(Fare fare) {
        return new FareResponse(fare.referencePoint(), fare.amount(), fare.validFrom());
    }
}
