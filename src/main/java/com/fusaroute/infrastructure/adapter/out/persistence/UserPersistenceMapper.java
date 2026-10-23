package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;

/**
 * Traduce entre el usuario de dominio y la entidad JPA, en ambos sentidos.
 *
 * El secreto TOTP cruza esta frontera cifrado/descifrado — por eso el mapper
 * recibe su valor como parametro explicito en vez de leerlo de {@link User}
 * directamente. Centralizar el cifrado en el adaptador y mantener al mapper
 * neutral es lo que mantiene el {@code domain.model.User} libre de la
 * libreria de criptografia.
 */
final class UserPersistenceMapper {

    private UserPersistenceMapper() {
    }

    /**
     * @param totpSecretCipher ciphertext ya cifrado (o {@code null} si el
     *                         usuario no esta enrolado).
     */
    static UserJpaEntity toEntity(User user, String totpSecretCipher) {
        return new UserJpaEntity(
                user.getId(),
                user.getName(),
                user.getEmail().value(),
                user.getPasswordHash(),
                user.getPhone(),
                user.getRole(),
                user.isActive(),
                user.getFailedLoginAttempts(),
                user.isTotpEnabled(),
                totpSecretCipher);
    }

    /**
     * @param totpSecretPlain secreto ya descifrado (o {@code null} si la
     *                        columna estaba en NULL).
     */
    static User toDomain(UserJpaEntity entity, String totpSecretPlain) {
        return new User(
                entity.getId(),
                entity.getName(),
                new Email(entity.getEmail()),
                entity.getPasswordHash(),
                entity.getPhone(),
                entity.getRole(),
                entity.isActive(),
                entity.getFailedLoginAttempts(),
                entity.isTotpEnabled(),
                totpSecretPlain);
    }
}
