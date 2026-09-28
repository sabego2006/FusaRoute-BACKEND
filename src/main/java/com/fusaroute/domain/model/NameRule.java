package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;

import java.util.Optional;

/**
 * Regla de validacion del nombre del usuario, compartida entre el registro
 * (RF-01) y la actualizacion de perfil (RF-03). Un solo lugar para la misma
 * regla — si cambia, cambia para ambos.
 */
public final class NameRule {

    public static final int MAX_LENGTH = 120;
    public static final String FIELD = "name";

    private NameRule() {
    }

    /** Valida y devuelve la violacion si la hay, o vacio si todo bien. */
    public static Optional<FieldViolation> validate(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.of(new FieldViolation(FIELD, "El nombre es obligatorio"));
        }
        if (trimmed.length() > MAX_LENGTH) {
            return Optional.of(new FieldViolation(FIELD,
                    "El nombre supera los " + MAX_LENGTH + " caracteres"));
        }
        return Optional.empty();
    }
}
