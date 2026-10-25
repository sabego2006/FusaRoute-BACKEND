package com.fusaroute.domain.port.in;

/**
 * Resultado del enrolamiento TOTP (SCRUM-179).
 *
 * El {@code secret} se devuelve en claro para el fallback de ingreso manual en
 * la app autenticadora (cuando el QR no se puede escanear). El
 * {@code otpauthUri} es el URI estandar que el frontend convierte en deep link
 * en movil o en QR en desktop. {@code qrPngBase64} es el PNG del QR codificado
 * en Base64 para que el frontend lo pinte sin librerias extra.
 */
public record TotpEnrollmentResult(String secret, String otpauthUri, String qrPngBase64) {
}
