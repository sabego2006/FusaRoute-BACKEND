package com.fusaroute.infrastructure.adapter.out.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(filled(32, (byte) 7));

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);

    @Test
    void ida_y_vuelta_devuelve_el_mismo_texto() {
        String secret = "JBSWY3DPEHPK3PXP";

        assertThat(cipher.decrypt(cipher.encrypt(secret))).isEqualTo(secret);
    }

    @Test
    void el_ciphertext_nunca_es_el_texto_plano() {
        assertThat(cipher.encrypt("JBSWY3DPEHPK3PXP")).doesNotContain("JBSWY3DPEHPK3PXP");
    }

    @Test
    void dos_cifrados_del_mismo_texto_son_distintos_por_el_nonce_fresco() {
        assertThat(cipher.encrypt("JBSWY3DPEHPK3PXP")).isNotEqualTo(cipher.encrypt("JBSWY3DPEHPK3PXP"));
    }

    @Test
    void descifrar_un_ciphertext_con_un_byte_alterado_lanza_excepcion() {
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("JBSWY3DPEHPK3PXP"));
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void descifrar_con_otra_clave_lanza_excepcion() {
        String encrypted = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThatThrownBy(() -> new AesGcmSecretCipher(OTHER_KEY).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void un_ciphertext_demasiado_corto_se_rechaza() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[10]);

        assertThatThrownBy(() -> cipher.decrypt(tooShort)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void una_clave_vacia_rompe_el_arranque() {
        assertThatThrownBy(() -> new AesGcmSecretCipher("  ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void una_clave_que_no_es_base64_rompe_el_arranque() {
        assertThatThrownBy(() -> new AesGcmSecretCipher("esto no es base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void una_clave_corta_rompe_el_arranque() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmSecretCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void una_clave_mas_larga_que_32_bytes_se_acepta_y_funciona() {
        AesGcmSecretCipher longKeyCipher = new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[48]));

        assertThat(longKeyCipher.decrypt(longKeyCipher.encrypt("abc"))).isEqualTo("abc");
    }

    private static byte[] filled(int size, byte value) {
        byte[] b = new byte[size];
        java.util.Arrays.fill(b, value);
        return b;
    }
}
