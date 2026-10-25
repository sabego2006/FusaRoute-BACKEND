package com.fusaroute.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code user_recovery_codes} (V7, SCRUM-179). Cada fila es un
 * codigo de respaldo del segundo factor de un usuario.
 */
@Entity
@Table(name = "user_recovery_codes")
public class UserRecoveryCodeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 72)
    private String codeHash;

    @Column(nullable = false)
    private boolean used;

    protected UserRecoveryCodeJpaEntity() {
        // Requerido por JPA.
    }

    public UserRecoveryCodeJpaEntity(Long id, Long userId, String codeHash, boolean used) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
        this.used = used;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public boolean isUsed() {
        return used;
    }

    public void markUsed() {
        this.used = true;
    }
}
