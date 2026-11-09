package com.fusaroute.domain.model;

import java.time.Instant;

/**
 * Un evento de uso del sistema: una busqueda de ruta o un toque de "Ya me subi"
 * (SCRUM-186). Sirve para medir el uso, no para seguir a nadie: <b>no lleva IP ni id
 * de usuario</b>, y esa ausencia es una decision, no un olvido.
 *
 * @param id               nulo mientras no se haya persistido.
 * @param occurredAt       cuando ocurrio; lo fija quien registra, no el escritor, porque
 *                         el guardado es asincrono y llega segundos despues.
 * @param type             busqueda o "Ya me subi".
 * @param originLabel      barrio de origen tal como se busco; puede ir nulo en BOARDED.
 * @param destinationLabel barrio de destino tal como se busco; puede ir nulo en BOARDED.
 * @param routeId          ruta elegida; nulo si no hubo ruta.
 * @param outcome          como termino.
 * @param etaSource        de donde salio el tiempo; nulo si no hubo tiempo.
 * @param latencyMs        duracion de la operacion en milisegundos; nulo si no se midio.
 * @param congestion       si la ruta pasaba por una ventana de congestion activa.
 */
public record UsageEvent(
        Long id,
        Instant occurredAt,
        UsageEventType type,
        String originLabel,
        String destinationLabel,
        Long routeId,
        SearchOutcome outcome,
        EtaSource etaSource,
        Integer latencyMs,
        boolean congestion) {

    public UsageEvent {
        if (occurredAt == null) {
            throw new IllegalArgumentException("El evento de uso necesita una fecha");
        }
        if (type == null) {
            throw new IllegalArgumentException("El evento de uso necesita un tipo");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("El evento de uso necesita un resultado");
        }
        if (latencyMs != null && latencyMs < 0) {
            throw new IllegalArgumentException("La latencia no puede ser negativa");
        }
    }

    /** Evento nuevo, sin id todavia. */
    public static UsageEvent of(Instant occurredAt, UsageEventType type, String originLabel,
                                String destinationLabel, Long routeId, SearchOutcome outcome,
                                EtaSource etaSource, Integer latencyMs, boolean congestion) {
        return new UsageEvent(null, occurredAt, type, originLabel, destinationLabel, routeId,
                outcome, etaSource, latencyMs, congestion);
    }
}
