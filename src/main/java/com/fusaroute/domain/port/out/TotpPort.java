package com.fusaroute.domain.port.out;

/**
 * Puerto de salida hacia la libreria TOTP (SCRUM-179). El dominio lo necesita
 * para generar el secreto en el enrolamiento, construir el URI estandar
 * {@code otpauth://} que la app autenticadora lee, y verificar los codigos. La
 * implementacion (hoy, dev.samstevens.totp) vive en un adaptador y es la unica
 * capa que importa esa libreria — el dominio no sabe cual algoritmo usa.
 */
public interface TotpPort {

    /**
     * Genera un secreto nuevo, en el formato que acepta
     * {@link #provisioningUri(String, String, String)} y
     * {@link #verifyCode(String, String)}. Hoy es una cadena Base32.
     */
    String generateSecret();

    /**
     * Construye el URI {@code otpauth://totp/...} que el frontend convierte en
     * deep link (movil) o en QR (desktop). {@code accountName} suele ser el
     * correo del usuario; {@code issuer} identifica a la app ("FusaRoute").
     */
    String provisioningUri(String secret, String accountName, String issuer);

    /**
     * Verifica un codigo de 6 digitos contra el secreto, con una tolerancia de
     * una ventana (±30 s) para absorber la deriva de reloj entre el movil y el
     * servidor.
     *
     * @return true si el codigo es valido en la ventana actual o adyacente.
     */
    boolean verifyCode(String secret, String code);

    /**
     * Dibuja el QR del {@code otpauthUri} como PNG codificado en Base64 (sin el
     * prefijo {@code data:}). El frontend lo pinta en un {@code <img>} sin
     * necesitar una libreria de QR.
     */
    String qrPngBase64(String otpauthUri);
}
