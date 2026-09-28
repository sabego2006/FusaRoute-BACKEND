package com.fusaroute.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila de tarifa de una ruta.
 *
 * @param referencePoint punto de referencia de bajada; {@code null} en la tarifa
 *     unica de una ruta urbana. No es una parada: en Fusagasuga no hay paradas formales.
 * @param amount precio en pesos colombianos.
 * @param orderIndex posicion dentro de la serie de vigencia a la que pertenece.
 * @param validFrom fecha desde la que rige; un cambio de tarifa es una fila nueva.
 */
public record Fare(String referencePoint, BigDecimal amount, int orderIndex, LocalDate validFrom) {
}
