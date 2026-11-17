package com.fusaroute.domain.port.in;

/**
 * Credenciales crudas del inicio de sesion, tal como llegan del mundo exterior y
 * antes de cualquier validacion o normalizacion.
 *
 * {@code totpCode} es opcional: solo se exige cuando el usuario ya tiene el
 * segundo factor activo (SCRUM-179). El mismo campo acepta un codigo TOTP de 6
 * digitos o un codigo de respaldo.
 *
 * {@code trustToken} tambien es opcional (decision del 17-nov-2026, SCRUM-196): el
 * dispositivo de confianza que el navegador guardo de un login anterior. Solo
 * tiene efecto si llega SIN {@code totpCode} y la cuenta es USER.
 */
public record LoginCommand(String email, String password, String totpCode, String trustToken) {

    /** Sobrecarga por retrocompatibilidad; sin codigo del segundo factor ni token de confianza. */
    public LoginCommand(String email, String password, String totpCode) {
        this(email, password, totpCode, null);
    }

    /** Sobrecarga por retrocompatibilidad; sin codigo del segundo factor. */
    public LoginCommand(String email, String password) {
        this(email, password, null, null);
    }
}
