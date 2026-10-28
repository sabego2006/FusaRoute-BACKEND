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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de la tabla {@code users}. Es el modelo de persistencia, distinto del
 * {@code domain.model.User} a proposito: un mapper traduce entre ambos.
 *
 * Solo mapea las columnas que el registro usa. {@code created_at}/{@code updated_at}
 * NO se mapean: los llena el default de la base (now()). De las columnas de
 * bloqueo solo se mapea failed_login_attempts (SCRUM-36); locked_until queda para
 * SCRUM-37.
 *
 * {@code totp_secret} guarda SIEMPRE el ciphertext (AES-GCM, V7, SCRUM-179):
 * en esta clase es solo una cadena; el cifrado y descifrado los hace el
 * adaptador de persistencia alrededor del mapeo.
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

    // La columna es SMALLINT (V1); sin el JdbcTypeCode, ddl-auto=validate espera INTEGER.
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    /** Ciphertext Base64 (AES-GCM) del secreto TOTP, o NULL si no esta enrolado. */
    @Column(name = "totp_secret", length = 255)
    private String totpSecret;

    protected UserJpaEntity() {
        // Requerido por JPA.
    }

    public UserJpaEntity(Long id, String name, String email, String passwordHash,
                         String phone, UserRole role, boolean active, int failedLoginAttempts,
                         boolean totpEnabled, String totpSecret) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.role = role;
        this.active = active;
        this.failedLoginAttempts = failedLoginAttempts;
        this.totpEnabled = totpEnabled;
        this.totpSecret = totpSecret;
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

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public String getTotpSecret() {
        return totpSecret;
    }
}
