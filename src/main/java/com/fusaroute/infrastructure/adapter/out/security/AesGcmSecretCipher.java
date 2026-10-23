package com.fusaroute.infrastructure.adapter.out.security;

import com.fusaroute.domain.port.out.SecretCipherPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrador AES-256-GCM para el secreto TOTP en reposo (SCRUM-179).
 *
 * SUBTASK MANUAL (regla del 30 % manual del CLAUDE.md del curso). Lo escribe un
 * integrante del equipo a mano; Claude solo guia. Esta clase queda como esqueleto
 * con el contrato y la configuracion; los metodos lanzan
 * {@link UnsupportedOperationException} hasta que esten implementados.
 *
 * <h2>Que implementar (guia, no codigo)</h2>
 * <ol>
 *   <li>En el constructor: decodificar {@code app.totp.encryption-key} desde
 *       Base64 y exigir >= 32 bytes (AES-256). Igual que {@code JwtConfig} con
 *       el secreto JWT: si falta o es debil, el arranque falla. Guardar la
 *       clave como {@code SecretKeySpec("AES")}.</li>
 *   <li>{@code encrypt(plaintext)}:
 *     <ul>
 *       <li>generar un nonce de 12 bytes con {@link java.security.SecureRandom};</li>
 *       <li>crear {@code Cipher.getInstance("AES/GCM/NoPadding")} e inicializar
 *           con {@link javax.crypto.spec.GCMParameterSpec} de 128 bits de tag;</li>
 *       <li>cifrar el {@code plaintext.getBytes(StandardCharsets.UTF_8)};</li>
 *       <li>concatenar {@code nonce || ciphertext || tag} en un solo arreglo y
 *           codificarlo con {@code Base64.getEncoder()}. Devolverlo como String.</li>
 *     </ul>
 *   </li>
 *   <li>{@code decrypt(ciphertext)}:
 *     <ul>
 *       <li>decodificar el Base64;</li>
 *       <li>separar los primeros 12 bytes como nonce y el resto como payload+tag;</li>
 *       <li>descifrar y devolver {@code new String(..., UTF_8)}.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>Reglas que no se rompen</h2>
 * <ul>
 *   <li>NUNCA reutilizar nonce con la misma clave. GCM pierde confidencialidad
 *       si eso pasa, por eso cada {@code encrypt} saca uno nuevo.</li>
 *   <li>NUNCA capturar y tragar la excepcion de autenticacion (tag invalido):
 *       si falla, debe propagarse para que la peticion caiga en 500 y quede en
 *       los logs. Un ciphertext manipulado NO debe parecer datos validos.</li>
 *   <li>NUNCA guardar la clave AES en {@code application.properties} con un
 *       default; va unicamente por variable de entorno
 *       ({@code TOTP_ENCRYPTION_KEY} en {@code .env.example}).</li>
 * </ul>
 *
 * <h2>Tests esperados</h2>
 * {@code AesGcmSecretCipherTest}: round-trip (encrypt + decrypt da el mismo
 * texto), dos encrypts del mismo texto producen ciphertext distintos (nonces
 * frescos), descifrar un ciphertext modificado un byte lanza excepcion, clave
 * corta o no-Base64 rompe el arranque.
 */
@Component
public class AesGcmSecretCipher implements SecretCipherPort {

    public AesGcmSecretCipher(@Value("${app.totp.encryption-key}") String base64Key) {
        // TODO (manual, SCRUM-179): validar la clave como describe el javadoc.
        // Hoy no se usa; el campo se recibe para que al implementar no cambie
        // la firma del constructor.
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "app.totp.encryption-key (TOTP_ENCRYPTION_KEY) no definido");
        }
    }

    @Override
    public String encrypt(String plaintext) {
        throw new UnsupportedOperationException(
                "AesGcmSecretCipher.encrypt pendiente (subtask manual SCRUM-179).");
    }

    @Override
    public String decrypt(String ciphertext) {
        throw new UnsupportedOperationException(
                "AesGcmSecretCipher.decrypt pendiente (subtask manual SCRUM-179).");
    }
}
