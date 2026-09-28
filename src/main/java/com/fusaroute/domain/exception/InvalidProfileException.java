package com.fusaroute.domain.exception;

import java.util.List;

/**
 * Excepción de dominio lanzada cuando los datos del perfil no son válidos.
 * Reutiliza la estructura de violaciones de campo para mantener consistencia con el registro.
 */
public class InvalidProfileException extends RuntimeException {
    private final List<String> violations;

    public InvalidProfileException(List<String> violations) {
        super("El perfil contiene datos inválidos.");
        this.violations = violations;
    }

    public List<String> getViolations() {
        return violations;
    }
}
