package com.fusaroute.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object que representa un correo electrónico validado.
 * Asegura que no se puedan crear usuarios con formatos de email inválidos.
 */
public final class Email {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-.]+(\\.[a-zA-Z0-9_+&*-.]+)*@([a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*)$"
    );
    private final String value;

    private Email(String value) {
        if (value == null || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Formato de correo electrónico inválido.");
        }
        this.value = value;
    }

    public static Email of(String value) {
        return new Email(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
