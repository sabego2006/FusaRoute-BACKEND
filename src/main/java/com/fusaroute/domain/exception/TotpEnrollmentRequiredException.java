package com.fusaroute.domain.exception;

import com.fusaroute.domain.model.IssuedToken;

import java.util.Objects;

/**
 * El usuario envio contrasena correcta pero aun no tiene TOTP activo (SCRUM-179).
 * Como el segundo factor es obligatorio, no se emite el JWT completo: se le
 * entrega un token de enrolamiento de vida corta y alcance acotado que SOLO
 * sirve para enrolar y confirmar el TOTP ({@code scope=totp_enroll}).
 *
 * Es una excepcion que lleva dato — rompe la regla general — para encajar con el
 * flujo del {@link com.fusaroute.application.usecase.LoginService}, que ya
 * expresa cada rechazo como una excepcion. El controller lee el token y lo
 * expone en el cuerpo del 401 ({@code enrollmentToken}).
 */
public class TotpEnrollmentRequiredException extends RuntimeException {

    private final IssuedToken enrollmentToken;

    public TotpEnrollmentRequiredException(IssuedToken enrollmentToken) {
        super("Se requiere activar el segundo factor");
        this.enrollmentToken = Objects.requireNonNull(enrollmentToken, "enrollmentToken");
    }

    public IssuedToken getEnrollmentToken() {
        return enrollmentToken;
    }
}
