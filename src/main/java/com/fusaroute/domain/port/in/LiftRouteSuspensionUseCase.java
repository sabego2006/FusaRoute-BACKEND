package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.SuspensionView;

/** El administrador reactiva la ruta a mano, antes del fin de la ventana (RF-12). */
public interface LiftRouteSuspensionUseCase {

    /**
     * @throws com.fusaroute.domain.exception.SuspensionNotFoundException si no existe
     * @throws com.fusaroute.domain.exception.RouteConflictException      si ya termino o ya estaba levantada
     */
    SuspensionView lift(Long suspensionId);
}
