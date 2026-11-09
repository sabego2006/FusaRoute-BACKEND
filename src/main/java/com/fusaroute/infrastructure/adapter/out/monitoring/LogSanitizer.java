package com.fusaroute.infrastructure.adapter.out.monitoring;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Quita secretos de un texto antes de que llegue a {@code app_logs} (SCRUM-186).
 *
 * <p>Un log guardado en la base vive 30 dias y lo lee cualquier administrador; un
 * secreto que se cuele ahi es un secreto que ya no se puede retirar con solo rotar la
 * variable de entorno del despliegue. Por eso la limpieza va en el escritor, no en cada
 * sitio que loguea: no depende de que quien escribe un {@code log.warn} se acuerde.
 *
 * <p>Es una red de seguridad por patrones, no una garantia matematica: no puede saber
 * que una cadena arbitraria es una contrasena. Cubre las formas en que este backend
 * maneja secretos de verdad (JWT, llave de Google en la URL, contrasenas en
 * formularios o JSON, encabezado Authorization, claves del TOTP y credenciales dentro
 * de una URL de conexion). La regla de fondo sigue siendo no loguear secretos.
 *
 * <p>Se sanea ANTES de recortar: si se recortara primero, un token cortado por la mitad
 * ya no coincidiria con el patron y su inicio se guardaria.
 */
final class LogSanitizer {

    static final String REDACTED = "[REDACTED]";

    /** Tope de entrada para acotar el costo de las expresiones regulares. */
    private static final int MAX_INPUT = 20_000;

    private static final String TRUNCATED_SUFFIX = "...[truncado]";

    /**
     * Nombres de campo cuyo valor es secreto. Se buscan en cualquier posicion (sirve
     * para {@code user_password}) pero exigen {@code :} o {@code =} despues, asi que
     * {@code tokenCount=5} no coincide.
     */
    private static final String SECRET_NAMES = String.join("|",
            "authorization", "proxy-authorization", "x-api-key",
            "password", "passwd", "pwd", "contrasena",
            "secret", "client_secret", "client-secret",
            "access_token", "refresh_token", "id_token", "token",
            "api[_-]?key", "apikey",
            "encryption[_.-]?key", "private[_.-]?key");

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            // JWT completo (tres segmentos base64url). Primero, para que el resto no lo parta.
            new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*"),
                    "[REDACTED_JWT]"),
            // Llave de API de Google: siempre empieza con AIza.
            new Rule(Pattern.compile("AIza[0-9A-Za-z_-]{20,}"), "[REDACTED_GOOGLE_KEY]"),
            // Credenciales embebidas en una URL: scheme://usuario:clave@host
            new Rule(Pattern.compile("(?i)(\\b[a-z][a-z0-9+.-]*://[^\\s:/@]+:)[^\\s@/]+(@)"),
                    "$1" + REDACTED + "$2"),
            // ?key=... / &key=... (asi viaja la llave de Google Maps en la URL)
            new Rule(Pattern.compile("(?i)([?&]key=)[^&\\s\"']+"), "$1" + REDACTED),
            // campo: valor, campo=valor, "campo":"valor". Cubre Authorization (con o sin esquema).
            new Rule(Pattern.compile("(?i)((?:" + SECRET_NAMES + ")[\"']?\\s*[:=]\\s*[\"']?)"
                    + "(?:(?:bearer|basic|digest)\\s+)?[^\\s&,;\"'}\\]]+"),
                    "$1" + REDACTED),
            // "Bearer xxxxxxxx" suelto, sin el nombre del encabezado.
            new Rule(Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}"), "Bearer " + REDACTED));

    private LogSanitizer() {
    }

    /** Devuelve el texto sin secretos y sin caracteres NUL; {@code null} se queda {@code null}. */
    static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        String result = text.length() > MAX_INPUT ? text.substring(0, MAX_INPUT) : text;
        // PostgreSQL rechaza el caracter NUL en un texto: un solo log con uno tumbaria el lote entero.
        result = result.replace("\u0000", "");
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }

    /** {@link #sanitize} y despues recorta a {@code maxLength} dejando una marca de recorte. */
    static String sanitizeAndTruncate(String text, int maxLength) {
        return truncate(sanitize(text), maxLength);
    }

    static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - TRUNCATED_SUFFIX.length()) + TRUNCATED_SUFFIX;
    }
}
