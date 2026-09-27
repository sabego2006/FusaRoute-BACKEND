package com.fusaroute.domain.exception;

import java.util.List;

/**
 * Reune todos los errores de validacion del registro (nombre, correo,
 * contrasena) en una sola respuesta. El caso de uso valida los tres campos
 * acumulando incumplimientos y lanza esta excepcion una sola vez, para que el
 * formulario de Angular los pinte juntos en lugar de forzar reintentos campo a
 * campo.
 */
public class InvalidRegistrationException extends RuntimeException {

    private final transient List<FieldViolation> violations;

    public InvalidRegistrationException(List<FieldViolation> violations) {
        super("Datos de registro invalidos");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
