package com.fusaroute.domain.exception;

import java.util.List;

/**
 * Errores de validacion al actualizar el perfil (RF-03). Reutiliza
 * {@link FieldViolation} para que el frontend pinte errores por campo
 * igual que en el registro.
 */
public class InvalidProfileException extends RuntimeException {

    private final transient List<FieldViolation> violations;

    public InvalidProfileException(List<FieldViolation> violations) {
        super("Datos del perfil invalidos");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
