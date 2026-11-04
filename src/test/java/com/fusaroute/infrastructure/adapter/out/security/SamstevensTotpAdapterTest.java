package com.fusaroute.infrastructure.adapter.out.security;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el adaptador TOTP real, sin mockear la libreria: es exactamente el
 * agujero que dejo pasar el esqueleto que lanzaba UnsupportedOperationException
 * mientras todos los tests de caso de uso mockeaban {@code TotpPort}.
 *
 * El codigo valido no se escribe a mano: se le pide a la MISMA libreria para el
 * paso de tiempo actual. Asi la prueba verifica que generar y verificar son
 * consistentes entre si, que es lo que la app autenticadora espera.
 */
class SamstevensTotpAdapterTest {

    private final SamstevensTotpAdapter adapter = new SamstevensTotpAdapter();

    @Test
    void genera_un_secreto_base32() {
        String secret = adapter.generateSecret();

        // DefaultSecretGenerator produce 32 caracteres del alfabeto Base32 (RFC 4648).
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(adapter.generateSecret()).isNotEqualTo(secret);
    }

    @Test
    void arma_el_uri_otpauth_con_los_parametros_que_entienden_las_apps() {
        String uri = adapter.provisioningUri("ABCDEFGHIJKLMNOP", "usuario@correo.com", "FusaRoute");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=ABCDEFGHIJKLMNOP");
        assertThat(uri).contains("issuer=FusaRoute");
        // SHA1 / 6 digitos / 30 s: cambiarlos rompe Microsoft y Google Authenticator.
        assertThat(uri).contains("algorithm=SHA1");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
        assertThat(uri).contains("usuario%40correo.com");
    }

    @Test
    void verifica_el_codigo_que_calcula_la_propia_libreria() throws Exception {
        String secret = adapter.generateSecret();
        long currentBucket = new SystemTimeProvider().getTime() / 30;
        String validCode = new DefaultCodeGenerator().generate(secret, currentBucket);

        assertThat(adapter.verifyCode(secret, validCode)).isTrue();
        assertThat(adapter.verifyCode(secret, "000000")).isFalse();
    }

    @Test
    void tolera_una_ventana_de_deriva_de_reloj() throws Exception {
        String secret = adapter.generateSecret();
        long previousBucket = new SystemTimeProvider().getTime() / 30 - 1;
        String previousCode = new DefaultCodeGenerator().generate(secret, previousBucket);

        // setAllowedTimePeriodDiscrepancy(1): el codigo de la ventana anterior sigue valiendo.
        assertThat(adapter.verifyCode(secret, previousCode)).isTrue();
    }

    @Test
    void entradas_invalidas_devuelven_false_sin_lanzar() {
        String secret = adapter.generateSecret();

        assertThat(adapter.verifyCode(null, "123456")).isFalse();
        assertThat(adapter.verifyCode(secret, null)).isFalse();
        assertThat(adapter.verifyCode(secret, "")).isFalse();
        assertThat(adapter.verifyCode(secret, "   ")).isFalse();
        assertThat(adapter.verifyCode(secret, "abcdef")).isFalse();
        assertThat(adapter.verifyCode(secret, "12345")).isFalse();
        assertThat(adapter.verifyCode("no-es-base32!", "123456")).isFalse();
    }

    @Test
    void dibuja_el_qr_como_png_en_base64() {
        String uri = adapter.provisioningUri(adapter.generateSecret(), "usuario@correo.com", "FusaRoute");

        byte[] png = Base64.getDecoder().decode(adapter.qrPngBase64(uri));

        // Firma PNG: 89 50 4E 47 0D 0A 1A 0A.
        assertThat(png).startsWith(
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A);
    }
}
