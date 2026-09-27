package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;

/**
 * Traduce entre el usuario de dominio y la entidad JPA, en ambos sentidos. Vivir
 * aqui, en infraestructura, es lo que mantiene el {@code domain.model.User} libre
 * de anotaciones de persistencia.
 */
final class UserPersistenceMapper {

    private UserPersistenceMapper() {
    }

    static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.getId(),
                user.getName(),
                user.getEmail().value(),
                user.getPasswordHash(),
                user.getPhone(),
                user.getRole(),
                user.isActive());
    }

    static User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(),
                entity.getName(),
                new Email(entity.getEmail()),
                entity.getPasswordHash(),
                entity.getPhone(),
                entity.getRole(),
                entity.isActive());
    }
}
