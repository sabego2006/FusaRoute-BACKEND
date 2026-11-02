package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.port.out.SecretCipherPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Cifrador AES-256-GCM para el secreto TOTP en reposo (SCRUM-179).
 *
 * <p>Formato de salida de {@link #encrypt(String)}: Base64 de {@code nonce(12) || ciphertext || tag(16)}.
 * El tag de autenticacion ya viene al final del resultado de {@code Cipher.doFinal}, asi que el
 * formato solo tiene que anteponer el nonce para poder descifrar.
 *
 * <p>Reglas que no se rompen:
 * <ul>
 *   <li>Cada {@code encrypt} genera un nonce nuevo: GCM pierde confidencialidad si se repite
 *       con la misma clave.</li>
 *   <li>La excepcion de autenticacion (tag invalido) se propaga y no se traga: un ciphertext
 *       manipulado no debe parecer un dato valido, debe caer en 500 y quedar en los logs.</li>
 *   <li>La clave llega solo por variable de entorno ({@code TOTP_ENCRYPTION_KEY}), sin valor por
 *       defecto.</li>
 * </ul>
 */
@Component
public class AesGcmSecretCipher implements SecretCipherPort {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(@Value("${app.totp.encryption-key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "app.totp.encryption-key (TOTP_ENCRYPTION_KEY) no definido");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.totp.encryption-key (TOTP_ENCRYPTION_KEY) no es Base64 valido", e);
        }
        if (raw.length < KEY_BYTES) {
            throw new IllegalStateException(
                    "app.totp.encryption-key (TOTP_ENCRYPTION_KEY) debe decodificar a al menos " + KEY_BYTES
                            + " bytes; genera una con: openssl rand -base64 32");
        }
        // AES-256 usa exactamente 32 bytes: si la clave trae mas, se toman los primeros 32.
        this.key = new SecretKeySpec(Arrays.copyOf(raw, KEY_BYTES), "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[NONCE_BYTES + encrypted.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
            System.arraycopy(encrypted, 0, out, NONCE_BYTES, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        Objects.requireNonNull(ciphertext, "ciphertext");
        byte[] in = Base64.getDecoder().decode(ciphertext);
        if (in.length < NONCE_BYTES + TAG_BYTES) {
            throw new IllegalArgumentException("Ciphertext demasiado corto para contener nonce y tag");
        }
        byte[] nonce = Arrays.copyOfRange(in, 0, NONCE_BYTES);
        byte[] payload = Arrays.copyOfRange(in, NONCE_BYTES, in.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Incluye AEADBadTagException: se propaga a proposito, un ciphertext alterado no se acepta.
            throw new IllegalStateException("No se pudo descifrar el secreto (clave incorrecta o dato alterado)", e);
        }
    }
}
