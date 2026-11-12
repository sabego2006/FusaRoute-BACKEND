package com.fusaroute.domain.exception;

import java.util.List;

/** Datos de la ruta invalidos al crearla o modificarla (RF-11). Misma estructura de errores por campo que el registro. */
public class InvalidRouteException extends RuntimeException {

    private final transient List<FieldViolation> violations;

    public InvalidRouteException(List<FieldViolation> violations) {
        super("Datos de la ruta invalidos");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
