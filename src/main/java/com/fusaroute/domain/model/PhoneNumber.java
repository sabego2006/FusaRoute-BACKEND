package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Numero de telefono como objeto de valor. Es opcional en el perfil del usuario
 * (RF-03): se completa despues del registro. Si se proporciona, debe tener entre
 * 7 y 15 digitos (rango que cubre fijos colombianos de 7 digitos y celulares con
 * indicativo internacional de hasta 15).
 */
public final class PhoneNumber {

    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d{7,15}$");
    public static final String FIELD = "phone";

    private PhoneNumber() {
    }

    /**
     * Valida el telefono crudo. Null o vacio se acepta (el campo es opcional);
     * si tiene valor, debe ser solo digitos entre 7 y 15.
     */
    public static Optional<FieldViolation> validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (!DIGITS_ONLY.matcher(trimmed).matches()) {
            return Optional.of(new FieldViolation(FIELD,
                    "El telefono debe tener entre 7 y 15 digitos numericos"));
        }
        return Optional.empty();
    }

    /** Normaliza: trim. Null se queda como null (campo opcional). */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
