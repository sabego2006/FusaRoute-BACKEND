package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.User;

/**
 * Caso de uso para actualizar los datos del perfil del usuario.
 */
public interface UpdateProfileUseCase {
    User execute(UpdateProfileCommand command);
}
