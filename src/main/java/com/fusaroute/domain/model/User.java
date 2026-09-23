package com.fusaroute.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio User.
 * Representa a un usuario del sistema FusaRoute.
 * Esta clase es un POJO puro, sin dependencias de frameworks (RNF-03).
 */
public class User {
    private final UUID id;
    private final String name;
    private final String email;
    private final String password;
    private boolean isActive;
    private int failedLoginAttempts;
    private LocalDateTime lockoutUntil;

    public User(UUID id, String name, String email, String password,
                   boolean isActive, int failedLoginAttempts, LocalDateTime lockoutUntil) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.isActive = isActive;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockoutUntil = lockoutUntil;
    }

    // Constructor para nuevos usuarios antes de ser guardados (ID generado por la DB o asignado)
    public User(String name, String email, String password) {
        this(UUID.randomUUID(), name, email, password, false, 0, null);
    }

    public void activate() {
        this.isActive = true;
    }

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lockoutUntil = null;
    }

    public void setLockout(int durationMinutes) {
        this.lockoutUntil = LocalDateTime.now().plusMinutes(durationMinutes);
    }

    public boolean isLockedOut() {
        if (lockoutUntil == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(lockoutUntil);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public LocalDateTime getLockoutUntil() {
        return lockoutUntil;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", isActive=" + isActive +
                ", failedAttempts=" + failedLoginAttempts +
                ", lockedUntil=" + lockoutUntil +
                '}';
    }
}
