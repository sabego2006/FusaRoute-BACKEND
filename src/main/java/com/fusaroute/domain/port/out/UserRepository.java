package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.User;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para el acceso a datos de usuarios.
 * Implementado por adaptadores de persistencia (ej. JPA).
 */
public interface UserRepository {
    User save(User user);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    void updateLockoutStatus(UUID id, int attempts, LocalDateTime lockoutUntil);
}
