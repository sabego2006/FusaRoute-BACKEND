package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FieldViolation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Politica de contrasena del registro (RF-01): minimo 8 caracteres, al menos una
 * mayuscula y al menos un numero.
 *
 * Ademas exige como maximo 72 bytes en UTF-8. Ese tope no es cosmetico: BCrypt
 * ignora todo lo que pase de 72 bytes, asi que dos contrasenas que solo difieran
 * despues del byte 72 quedarian con el mismo hash, y Spring Security 6.5 lanza
 * excepcion si el texto los excede. Con tildes o emoji un caracter ocupa varios
 * bytes, por eso el tope se mide en bytes, no en caracteres.
 *
 * Devuelve TODOS los incumplimientos, no se detiene en el primero, para que el
 * caso de uso los muestre juntos.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_BYTES = 72;
    public static final String FIELD = "password";

    private PasswordPolicy() {
    }

    public static List<FieldViolation> validate(String raw) {
        List<FieldViolation> violations = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            violations.add(new FieldViolation(FIELD, "La contrasena es obligatoria"));
            return violations;
        }
        if (raw.length() < MIN_LENGTH) {
            violations.add(new FieldViolation(FIELD, "La contrasena debe tener al menos " + MIN_LENGTH + " caracteres"));
        }
        if (raw.chars().noneMatch(Character::isUpperCase)) {
            violations.add(new FieldViolation(FIELD, "La contrasena debe incluir al menos una mayuscula"));
        }
        if (raw.chars().noneMatch(Character::isDigit)) {
            violations.add(new FieldViolation(FIELD, "La contrasena debe incluir al menos un numero"));
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            violations.add(new FieldViolation(FIELD, "La contrasena supera los " + MAX_BYTES + " bytes permitidos"));
        }
        return violations;
    }
}
