package com.fusaroute.domain.model;

import java.time.Instant;

/**
 * Una ruta que serviria el trayecto buscado pero esta efectivamente suspendida (RF-12). Viaja en el
 * "sin ruta" de la busqueda para decirle al usuario cuando vuelve.
 *
 * @param endsAt fin de la ventana vigente
 */
public record SuspendedRoute(Long routeId, String routeName, Instant endsAt) {
}
