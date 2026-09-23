package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.out.UserRepository;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia para el repositorio de usuarios.
 * Traduce el modelo de dominio a una entidad JPA.
 */
@Component
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository springDataRepository;

    public JpaUserRepository(SpringDataUserRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = UserMapper.toEntity(user);
        UserEntity savedEntity = springDataRepository.save(entity);
        return UserMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springDataRepository.findByEmail(email)
                .map(UserMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springDataRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public void updateLockoutStatus(UUID id, int attempts, LocalDateTime lockoutUntil) {
        springDataRepository.updateLockoutStatus(id, attempts, lockoutUntil);
    }

    // --- Entidad JPA Interna ---
    @Entity
    @Table(name = "users")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserEntity {
        @Id
        private UUID id;

        @Column(nullable = false)
        private String name;

        @Column(nullable = false, unique = true)
        private String email;

        @Column(nullable = false)
        private String password;

        @Column(nullable = false)
        private boolean isActive;

        @Column(nullable = false)
        private int failedLoginAttempts;

        private LocalDateTime lockoutUntil;
    }

    // --- Repositorio de Spring Data ---
    public interface SpringDataUserRepository extends JpaRepository<UserEntity, UUID> {
        Optional<UserEntity> findByEmail(String email);
        boolean existsByEmail(String email);

        @Modifying
        @Query("UPDATE UserEntity u SET u.failedLoginAttempts = :attempts, u.lockoutUntil = :lockoutUntil WHERE u.id = :id")
        void updateLockoutStatus(UUID id, int attempts, LocalDateTime lockoutUntil);
    }

    // --- Mapper interno ---
    private static class UserMapper {
        static UserEntity toEntity(User user) {
            return UserEntity.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .password(user.getPassword())
                    .isActive(user.isActive())
                    .failedLoginAttempts(user.getFailedLoginAttempts())
                    .lockoutUntil(user.getLockoutUntil())
                    .build();
        }

        static User toDomain(UserEntity entity) {
            return new User(
                    entity.getId(),
                    entity.getName(),
                    entity.getEmail(),
                    entity.getPassword(),
                    entity.isActive(),
                    entity.getFailedLoginAttempts(),
                    entity.getLockoutUntil()
            );
        }
    }
}
