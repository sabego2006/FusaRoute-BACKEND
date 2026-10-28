package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.User;

/**
 * Puerto de entrada del registro de usuarios (RF-01). Es lo que el mundo exterior
 * (el controller web) puede pedirle al dominio, sin conocer como se implementa.
 */
public interface RegisterUserUseCase {

    /**
     * Da de alta un usuario nuevo.
     *
     * @throws com.fusaroute.domain.exception.InvalidRegistrationException si el
     *     nombre, el correo o la contrasena no cumplen las reglas.
     * @throws com.fusaroute.domain.exception.EmailAlreadyRegisteredException si el
     *     correo ya tiene una cuenta.
     */
    User register(RegisterUserCommand command);
}
