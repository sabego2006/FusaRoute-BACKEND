package com.fusaroute.domain.exception;

import java.util.List;

/** Datos de la suspension invalidos (RF-12). Misma estructura de errores por campo que el CRUD de rutas. */
public class InvalidSuspensionException extends RuntimeException {

    private final transient List<FieldViolation> violations;

    public InvalidSuspensionException(List<FieldViolation> violations) {
        super("Datos de la suspension invalidos");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
