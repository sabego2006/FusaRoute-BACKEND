package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.port.out.TotpPort;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Adaptador TOTP sobre {@code dev.samstevens.totp} (SCRUM-179).
 *
 * <p>Parametros fijos: SHA1, 6 digitos, periodo de 30 s. Son los que entienden
 * Microsoft y Google Authenticator; cambiarlos haria que los codigos de la app
 * no cuadraran con los del servidor, asi que no son configurables a proposito.
 *
 * <p>La verificacion tolera una ventana ({@code setAllowedTimePeriodDiscrepancy(1)})
 * para absorber ±30 s de deriva de reloj entre el movil y el servidor.
 *
 * <p>Ni el secreto ni el codigo se registran en los logs: {@code secret} es
 * material de clave y {@code code} es una credencial efimera.
 *
 * <p>La libreria queda confinada en este archivo por regla hexagonal — el
 * dominio solo conoce {@link TotpPort} —, y {@code HexagonalArchitectureTest}
 * lo verifica.
 */
@Component
public class SamstevensTotpAdapter implements TotpPort {

    private static final HashingAlgorithm ALGORITHM = HashingAlgorithm.SHA1;
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;

    /** Todos son stateless y thread-safe, y el adaptador es un singleton de Spring. */
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = newCodeVerifier();

    private static CodeVerifier newCodeVerifier() {
        DefaultCodeVerifier verifier =
                new DefaultCodeVerifier(new DefaultCodeGenerator(ALGORITHM, DIGITS), new SystemTimeProvider());
        verifier.setTimePeriod(PERIOD_SECONDS);
        verifier.setAllowedTimePeriodDiscrepancy(1);
        return verifier;
    }

    @Override
    public String generateSecret() {
        return secretGenerator.generate();
    }

    @Override
    public String provisioningUri(String secret, String accountName, String issuer) {
        return qrData(secret, accountName, issuer).getUri();
    }

    @Override
    public boolean verifyCode(String secret, String code) {
        // El contrato del puerto exige false, no excepcion: LoginService y
        // ConfirmTotpService encadenan esta llamada con el intento de codigo de
        // respaldo, y una excepcion aqui se volveria un 500 en vez de un 401.
        if (secret == null || secret.isBlank() || code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (!trimmed.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        try {
            return codeVerifier.isValidCode(secret, trimmed);
        } catch (RuntimeException ignored) {
            // Secreto que no es Base32 valido: la fila esta corrupta, no es un
            // codigo correcto. Se rechaza sin loggear el secreto.
            return false;
        }
    }

    @Override
    public String qrPngBase64(String otpauthUri) {
        try {
            return Base64.getEncoder().encodeToString(qrGenerator.generate(parseUri(otpauthUri)));
        } catch (dev.samstevens.totp.exceptions.QrGenerationException e) {
            throw new IllegalStateException("No se pudo generar el QR del segundo factor", e);
        }
    }

    private static QrData qrData(String secret, String accountName, String issuer) {
        return new QrData.Builder()
                .label(accountName)
                .secret(secret)
                .issuer(issuer)
                .algorithm(ALGORITHM)
                .digits(DIGITS)
                .period(PERIOD_SECONDS)
                .build();
    }

    /**
     * Reconstruye el {@link QrData} a partir del URI, porque
     * {@link QrGenerator#generate(QrData)} no acepta una cadena arbitraria.
     *
     * <p>El resultado se compara contra el URI de entrada: si no coincide, el QR
     * codificaria algo distinto del deep link y el usuario veria dos cuentas
     * distintas en su app. Mejor fallar aqui que entregar un QR que no sirve.
     */
    private static QrData parseUri(String otpauthUri) {
        if (otpauthUri == null || !otpauthUri.startsWith("otpauth://totp/")) {
            throw new IllegalArgumentException("No es un URI otpauth://totp/");
        }
        URI uri = URI.create(otpauthUri);
        Map<String, String> params = queryParams(uri.getRawQuery());
        String label = decode(otpauthUri.substring("otpauth://totp/".length()).split("\\?", 2)[0]);

        QrData rebuilt = new QrData.Builder()
                .label(label)
                .secret(params.get("secret"))
                .issuer(params.get("issuer"))
                .algorithm(ALGORITHM)
                .digits(DIGITS)
                .period(PERIOD_SECONDS)
                .build();

        if (!otpauthUri.equals(rebuilt.getUri())) {
            throw new IllegalArgumentException("El URI otpauth no se pudo reconstruir sin perder informacion");
        }
        return rebuilt;
    }

    private static Map<String, String> queryParams(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                params.put(decode(parts[0]), decode(parts[1]));
            }
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
