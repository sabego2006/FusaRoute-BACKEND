package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.SuspensionView;

/** El administrador suspende una ruta durante una ventana de tiempo (RF-12). */
public interface CreateRouteSuspensionUseCase {

    /**
     * @throws com.fusaroute.domain.exception.RouteNotFoundException        si la ruta no existe
     * @throws com.fusaroute.domain.exception.InvalidSuspensionException    si la ventana o el motivo no cumplen
     * @throws com.fusaroute.domain.exception.RouteConflictException        si la ruta esta INACTIVA o la ventana
     *                                                                       se solapa con otra no levantada
     */
    SuspensionView create(CreateRouteSuspensionCommand command);
}
