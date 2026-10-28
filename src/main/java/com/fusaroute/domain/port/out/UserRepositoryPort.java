package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;

import java.util.Optional;

/**
 * Puerto de salida hacia el almacen de usuarios. El dominio lo necesita, pero no
 * sabe si detras hay JPA, otra base o un mock de test.
 */
public interface UserRepositoryPort {

    boolean existsByEmail(Email email);

    Optional<User> findByEmail(Email email);

    Optional<User> findById(Long id);

    /**
     * Persiste el usuario y devuelve la version con id asignado.
     *
     * @throws com.fusaroute.domain.exception.EmailAlreadyRegisteredException si en
     *     una carrera dos altas del mismo correo chocan contra el UNIQUE de la base.
     */
    User save(User user);
}
