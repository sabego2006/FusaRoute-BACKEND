package com.fusaroute.domain.port.in;

/**
 * Puerto de entrada del inicio de sesion (RF-02). Es lo que el mundo exterior (el
 * controller web) puede pedirle al dominio, sin conocer como se implementa.
 */
public interface LoginUseCase {

    /**
     * Autentica con correo y contrasena y emite el token de acceso.
     *
     * @throws com.fusaroute.domain.exception.InvalidCredentialsException por
     *     cualquier motivo de rechazo, siempre con el mismo mensaje generico.
     */
    LoginResult login(LoginCommand command);
}
