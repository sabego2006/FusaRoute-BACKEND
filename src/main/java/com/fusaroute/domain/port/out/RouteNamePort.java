package com.fusaroute.domain.port.out;

import java.util.Collection;
import java.util.Map;

/**
 * Nombres de ruta por id, sin cargar trazado, barrios ni tarifas (SCRUM-26). Incluye las rutas INACTIVAS y
 * las suspendidas: el ranking del panel es historico.
 */
@FunctionalInterface
public interface RouteNamePort {

    /** Solo los ids que existen; un id que ya no esta en {@code routes} no aparece en el mapa. */
    Map<Long, String> findNamesByIds(Collection<Long> ids);
}
