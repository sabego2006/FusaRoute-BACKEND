package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador de salida que implementa {@link UserRepositoryPort} sobre Spring Data.
 */
@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;

    public UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(Email email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    @Override
    public Optional<User> findByEmail(Email email) {
        return repository.findByEmail(email.value()).map(UserPersistenceMapper::toDomain);
    }
    }

    @Override
    public User save(User user) {
        try {
            return UserPersistenceMapper.toDomain(repository.save(UserPersistenceMapper.toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            // El existsByEmail del caso de uso no cierra la ventana de carrera:
            // dos altas del mismo correo pueden pasar el chequeo a la vez y solo
            // una gana el UNIQUE uq_users_email. Traducir el choque a la excepcion
            // de dominio da un 409 limpio en vez de un 500.
            throw new EmailAlreadyRegisteredException();
        }
    }
}
