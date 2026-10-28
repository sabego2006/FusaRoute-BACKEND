package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.User;

/** Consultar el perfil del usuario autenticado (RF-03, GET /api/users/me). */
public interface GetProfileUseCase {

    /**
     * @param userId id del usuario autenticado, extraido del JWT
     * @throws com.fusaroute.domain.exception.UserNotFoundException si el id no existe
     */
    User getProfile(Long userId);
}
