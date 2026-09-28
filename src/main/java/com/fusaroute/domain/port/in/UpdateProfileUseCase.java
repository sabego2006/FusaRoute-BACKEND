package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.User;

/** Actualizar los datos editables del perfil (RF-03, PUT /api/users/me). */
public interface UpdateProfileUseCase {

    /**
     * @throws com.fusaroute.domain.exception.InvalidProfileException si algun campo no pasa validacion
     * @throws com.fusaroute.domain.exception.UserNotFoundException si el id no existe
     * @throws com.fusaroute.domain.exception.EmailAlreadyRegisteredException si el nuevo correo ya esta en uso
     */
    User updateProfile(UpdateProfileCommand command);
}
