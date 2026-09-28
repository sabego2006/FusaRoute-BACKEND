package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.User;

/**
 * Caso de uso para cambiar la contraseña del usuario autenticado.
 */
public interface ChangePasswordUseCase {
    void execute(ChangePasswordCommand command);
}
