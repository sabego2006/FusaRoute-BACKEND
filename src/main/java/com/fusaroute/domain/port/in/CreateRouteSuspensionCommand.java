package com.fusaroute.domain.port.in;

import java.time.Instant;

/**
 * Pedido de suspender una ruta. {@code startsAt} y {@code endsAt} pueden venir nulos: el caso de uso
 * los reporta como "obligatorio" junto con el resto de violaciones.
 *
 * @param adminId el administrador que la crea, tomado del JWT
 */
public record CreateRouteSuspensionCommand(Long routeId, Instant startsAt, Instant endsAt, String reason,
                                           Long adminId) {
}
