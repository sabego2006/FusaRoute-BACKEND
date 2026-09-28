package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.User;
import java.util.Optional;

/**
 * Caso de uso para obtener el perfil del usuario autenticado.
 */
public interface GetProfileUseCase {
    User execute(Long userId);
}
