package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.RouteSuspension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistencia de las suspensiones de ruta (RF-12). Una levantada se conserva: nunca se borra. */
public interface RouteSuspensionRepositoryPort {

    /** Guarda una suspension nueva; devuelve la fila con su id y el nombre de la ruta. */
    RouteSuspension save(RouteSuspension suspension, Long createdBy);

    Optional<RouteSuspension> findById(Long id);

    /** Todas, en cualquier estado y sin orden garantizado: el orden del listado lo decide el caso de uso. */
    List<RouteSuspension> findAll();

    /** Las no levantadas de una ruta (vigentes, proximas y ya terminadas), para detectar solapes. */
    List<RouteSuspension> findNotLiftedByRoute(Long routeId);

    /** Fija {@code lifted_at} y devuelve la fila actualizada. */
    RouteSuspension markLifted(Long id, Instant at);
}
