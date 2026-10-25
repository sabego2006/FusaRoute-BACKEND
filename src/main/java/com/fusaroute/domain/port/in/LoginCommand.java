package com.fusaroute.domain.port.in;

/**
 * Credenciales crudas del inicio de sesion, tal como llegan del mundo exterior y
 * antes de cualquier validacion o normalizacion.
 *
 * {@code totpCode} es opcional: solo se exige cuando el usuario ya tiene el
 * segundo factor activo (SCRUM-179). El mismo campo acepta un codigo TOTP de 6
 * digitos o un codigo de respaldo.
 */
public record LoginCommand(String email, String password, String totpCode) {

    /** Sobrecarga por retrocompatibilidad; sin codigo del segundo factor. */
    public LoginCommand(String email, String password) {
        this(email, password, null);
    }
}
