package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.TotpEnrollmentResult;

/**
 * Respuesta de {@code POST /api/users/me/totp/enroll} (SCRUM-179). Trae el
 * secreto en claro (para ingreso manual en la app autenticadora), el URI
 * {@code otpauth://} y el QR como PNG Base64. Es un cuerpo "sensible" — solo
 * se devuelve al usuario que acaba de autenticarse.
 */
public record TotpEnrollResponse(String secret, String otpauthUri, String qrPngBase64) {

    static TotpEnrollResponse from(TotpEnrollmentResult result) {
        return new TotpEnrollResponse(result.secret(), result.otpauthUri(), result.qrPngBase64());
    }
}
