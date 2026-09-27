package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code users}. Es el modelo de persistencia, distinto del
 * {@code domain.model.User} a proposito: un mapper traduce entre ambos.
 *
 * Solo mapea las columnas que el registro usa. {@code created_at}/{@code updated_at}
 * NO se mapean: los llena el default de la base (now()). Las columnas de bloqueo
 * (failed_login_attempts, locked_until) quedan para SCRUM-155.
 */
@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    protected UserJpaEntity() {
        // Requerido por JPA.
    }

    public UserJpaEntity(Long id, String name, String email, String passwordHash,
                         String phone, UserRole role, boolean active) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.role = role;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
