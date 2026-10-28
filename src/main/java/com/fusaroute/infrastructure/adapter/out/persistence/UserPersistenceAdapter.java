package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.out.SecretCipherPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador de salida que implementa {@link UserRepositoryPort} sobre Spring Data.
 *
 * SCRUM-179: el secreto TOTP viaja en claro por el dominio y en ciphertext en la
 * base. Este adaptador es la unica frontera entre ambos mundos: cifra justo
 * antes de guardar y descifra justo despues de leer. La libreria de criptografia
 * (hoy, AES-GCM) vive detras de {@link SecretCipherPort}.
 */
@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;
    private final SecretCipherPort cipher;

    public UserPersistenceAdapter(SpringDataUserRepository repository, SecretCipherPort cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    public boolean existsByEmail(Email email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return repository.findByEmail(email.value()).map(this::decryptAndMap);
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(this::decryptAndMap);
    }

    @Override
    public User save(User user) {
        String cipherSecret = user.getTotpSecret() == null ? null : cipher.encrypt(user.getTotpSecret());
        try {
            UserJpaEntity saved = repository.save(UserPersistenceMapper.toEntity(user, cipherSecret));
            // Al recibir la fila ya persistida, se devuelve el User con el
            // secreto EN CLARO: el que vino en la peticion. Asi un llamador
            // (p. ej. ConfirmTotpService) sigue viendo el mismo valor que
            // envio, sin tener que descifrar de nuevo.
            return UserPersistenceMapper.toDomain(saved, user.getTotpSecret());
        } catch (DataIntegrityViolationException e) {
            // El existsByEmail del caso de uso no cierra la ventana de carrera:
            // dos altas del mismo correo pueden pasar el chequeo a la vez y solo
            // una gana el UNIQUE uq_users_email. Traducir el choque a la excepcion
            // de dominio da un 409 limpio en vez de un 500.
            throw new EmailAlreadyRegisteredException();
        }
    }

    private User decryptAndMap(UserJpaEntity entity) {
        String plain = entity.getTotpSecret() == null ? null : cipher.decrypt(entity.getTotpSecret());
        return UserPersistenceMapper.toDomain(entity, plain);
    }
}
