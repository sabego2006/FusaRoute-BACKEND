package com.fusaroute.domain.port.in;

/** Cambiar la contrasena del usuario autenticado (RF-03, PUT /api/users/me/password). */
public interface ChangePasswordUseCase {

    /**
     * @throws com.fusaroute.domain.exception.UserNotFoundException si el id no existe
     * @throws com.fusaroute.domain.exception.IncorrectCurrentPasswordException si la contrasena actual no coincide
     * @throws com.fusaroute.domain.exception.InvalidProfileException si la nueva contrasena no cumple la politica
     */
    void changePassword(ChangePasswordCommand command);
}
