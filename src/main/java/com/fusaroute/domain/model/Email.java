package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Correo como objeto de valor: siempre normalizado y con formato valido.
 *
 * La normalizacion (trim + minusculas) es la que hace util al UNIQUE de la base.
 * El constraint de Postgres distingue mayusculas, asi que sin normalizar,
 * {@code Ana@x.co} y {@code ana@x.co} serian dos cuentas distintas. Guardar
 * siempre la forma normalizada cierra ese hueco.
 */
public record Email(String value) {

    // Formato deliberadamente simple: algo@algo.algo sin espacios. No se
    // persigue el RFC 5322 completo; la validacion real de un correo es que
    // reciba el mensaje, y este semestre el registro activa sin verificacion.
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_LENGTH = 180;
    public static final String FIELD = "email";

    /** Constructor canonico: exige un valor ya normalizado y valido. */
    public Email {
        if (value == null || !FORMAT.matcher(value).matches() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Correo invalido: " + value);
        }
    }

    /** trim + minusculas. Null se traduce a cadena vacia para validar sin NPE. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    /**
     * Valida el correo crudo sin construirlo. Devuelve el incumplimiento si lo
     * hay, para que el caso de uso lo acumule junto a los demas campos.
     */
    public static Optional<FieldViolation> validate(String raw) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return Optional.of(new FieldViolation(FIELD, "El correo es obligatorio"));
        }
        if (normalized.length() > MAX_LENGTH) {
            return Optional.of(new FieldViolation(FIELD, "El correo supera los " + MAX_LENGTH + " caracteres"));
        }
        if (!FORMAT.matcher(normalized).matches()) {
            return Optional.of(new FieldViolation(FIELD, "El formato del correo no es valido"));
        }
        return Optional.empty();
    }

    /** Normaliza y construye. Asume que {@link #validate} ya dio verde. */
    public static Email of(String raw) {
        return new Email(normalize(raw));
    }
}
