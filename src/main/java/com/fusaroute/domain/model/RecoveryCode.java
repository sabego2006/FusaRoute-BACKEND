package com.fusaroute.domain.model;

import java.util.Objects;

/**
 * Codigo de respaldo del segundo factor (SCRUM-179). Es la unica salida para un
 * usuario que perdio su telefono, porque la recuperacion de contrasena esta
 * fuera de alcance este semestre.
 *
 * El dominio solo conoce el hash — el codigo en claro se muestra UNA sola vez al
 * usuario y se descarta. Cada codigo se consume una sola vez: {@code used}
 * marca si ya se gasto.
 */
public final class RecoveryCode {

    private final Long id;
    private final Long userId;
    private final String codeHash;
    private final boolean used;

    public RecoveryCode(Long id, Long userId, String codeHash, boolean used) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.used = used;
    }

    /** Factory para un codigo recien generado, aun sin id ni consumo. */
    public static RecoveryCode newFor(Long userId, String codeHash) {
        return new RecoveryCode(null, userId, codeHash, false);
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

    /** Copia marcada como consumida. */
    public RecoveryCode markUsed() {
        return new RecoveryCode(id, userId, codeHash, true);
    }
}
