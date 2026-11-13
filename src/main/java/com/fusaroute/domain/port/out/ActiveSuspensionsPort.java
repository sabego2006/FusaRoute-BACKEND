package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.RouteSuspension;

import java.time.Instant;
import java.util.List;

/**
 * El predicado "efectivamente suspendida", en un solo lugar: suspensiones con
 * {@code startsAt <= instante < endsAt} y sin levantar. Lo implementa una sola consulta del adaptador
 * de persistencia; la lectura publica de rutas, la busqueda y los avisos dependen de esta misma consulta
 * (sin scheduler: la ruta vuelve sola cuando la ventana se cierra).
 */
@FunctionalInterface
public interface ActiveSuspensionsPort {

    /** Ordenadas por fin de ventana (la que termina antes, primero) y luego por id. */
    List<RouteSuspension> findEffectiveAt(Instant now);
}
