package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.port.out.TotpPort;
import org.springframework.stereotype.Component;

/**
 * Adaptador TOTP sobre {@code dev.samstevens.totp} (SCRUM-179).
 *
 * SUBTASK MANUAL (regla del 30 % manual del CLAUDE.md del curso). Lo escribe un
 * integrante del equipo a mano; Claude solo guia. Esta clase queda como esqueleto
 * con el contrato y la libreria elegida en el {@code pom.xml}
 * ({@code dev.samstevens.totp:totp:1.7.1}); los metodos lanzan
 * {@link UnsupportedOperationException} hasta que esten implementados.
 *
 * <h2>Que implementar (guia, no codigo)</h2>
 * <ol>
 *   <li>{@code generateSecret()}:
 *       {@code new DefaultSecretGenerator().generate()}. Devuelve una cadena
 *       Base32 compatible con la app autenticadora.</li>
 *   <li>{@code provisioningUri(secret, accountName, issuer)}:
 *       armar un {@code QrData} con {@code label(accountName)},
 *       {@code secret(secret)}, {@code issuer(issuer)}, {@code algorithm(HashingAlgorithm.SHA1)},
 *       {@code digits(6)} y {@code period(30)} — son los valores por defecto que
 *       entienden Microsoft/Google Authenticator —, y devolver {@code qrData.getUri()}.</li>
 *   <li>{@code verifyCode(secret, code)}:
 *       crear un {@code CodeVerifier} con
 *       {@code DefaultCodeGenerator()} y {@code SystemTimeProvider()}; configurar
 *       {@code setAllowedTimePeriodDiscrepancy(1)} para absorber ±30 s de deriva
 *       de reloj entre el movil y el servidor; devolver
 *       {@code verifier.isValidCode(secret, code)}. Si {@code code} es null o no
 *       es numerico, devolver false sin lanzar.</li>
 *   <li>{@code qrPngBase64(otpauthUri)}:
 *       {@code QrGenerator qr = new ZxingPngQrGenerator();}
 *       reutilizar {@code QrData} (el metodo recibe el URI armado; si fuera
 *       necesario, parsearlo con {@code QrData.Builder()}); llamar
 *       {@code qr.generate(data)} para los bytes PNG y devolver
 *       {@code Base64.getEncoder().encodeToString(bytes)}.</li>
 * </ol>
 *
 * <h2>Reglas que no se rompen</h2>
 * <ul>
 *   <li>NUNCA cambiar los parametros de verificacion a {@code digits(8)} o
 *       {@code period(60)}: romperian la compatibilidad con Microsoft/Google
 *       Authenticator y los codigos no cuadrarian.</li>
 *   <li>NUNCA registrar el secreto o el codigo en los logs. {@code secret} es
 *       material de clave; {@code code} es efimero pero sigue siendo credencial.</li>
 *   <li>La libreria {@code dev.samstevens.totp} queda confinada en este archivo
 *       por regla hexagonal; el dominio solo conoce {@link TotpPort}.</li>
 * </ul>
 *
 * <h2>Tests esperados</h2>
 * {@code SamstevensTotpAdapterTest}: generar secreto da una cadena Base32 de
 * longitud fija; {@code provisioningUri} empieza por
 * {@code otpauth://totp/FusaRoute:correo?...secret=...&issuer=FusaRoute};
 * {@code verifyCode} acepta el codigo que calcula la propia libreria para
 * {@code SystemTimeProvider} y rechaza "000000"; verifyCode con secreto o
 * codigo nulo devuelve false sin lanzar.
 */
@Component
public class SamstevensTotpAdapter implements TotpPort {

    @Override
    public String generateSecret() {
        throw new UnsupportedOperationException(
                "SamstevensTotpAdapter.generateSecret pendiente (subtask manual SCRUM-179).");
    }

    @Override
    public String provisioningUri(String secret, String accountName, String issuer) {
        throw new UnsupportedOperationException(
                "SamstevensTotpAdapter.provisioningUri pendiente (subtask manual SCRUM-179).");
    }

    @Override
    public boolean verifyCode(String secret, String code) {
        throw new UnsupportedOperationException(
                "SamstevensTotpAdapter.verifyCode pendiente (subtask manual SCRUM-179).");
    }

    @Override
    public String qrPngBase64(String otpauthUri) {
        throw new UnsupportedOperationException(
                "SamstevensTotpAdapter.qrPngBase64 pendiente (subtask manual SCRUM-179).");
    }
}
