package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.User;
import java.util.Optional;

/**
 * Puerto de entrada para la funcionalidad de creación de usuarios.
 * Define el contrato que el mundo exterior (Controladores) usa para interactuar con el dominio.
 */
public interface CreateUserUseCasePort {
    User createUser(String name, String email, String password);
}
