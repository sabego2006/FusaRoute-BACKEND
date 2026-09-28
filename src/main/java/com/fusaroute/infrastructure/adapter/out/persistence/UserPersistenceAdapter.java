package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;

    public UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(com.fusaroute.domain.model.Email email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public Optional<User> findByEmail(com.fusaroute.domain.model.Email email) {
        return repository.findByEmail(email.value()).map(UserPersistenceMapper::toDomain);
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(UserPersistenceMapper::toDomain);
    }

    @Override
    public User save(User user) {
        try {
            return UserPersistenceMapper.toDomain(repository.save(UserPersistenceMapper.toEntity(user)));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new com.fusaroute.domain.exception.EmailAlreadyRegisteredException();
        }
    }
}
