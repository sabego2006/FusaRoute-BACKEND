package com.fusaroute.domain.port.out;

/**
 * Puerto de salida para cifrar y descifrar un secreto en reposo (SCRUM-179).
 *
 * BCrypt no sirve para esto: el secreto TOTP necesita recuperarse para
 * recalcular los codigos a cada login, asi que el guardado tiene que ser
 * REVERSIBLE. La implementacion canonica usa AES-256-GCM con clave en variable
 * de entorno; si roban la base sin la clave, el ciphertext no sirve. Guardar el
 * secreto en claro seria un hallazgo de seguridad.
 */
public interface SecretCipherPort {

    /**
     * Cifra el texto plano. La implementacion define el formato de salida
     * (hoy, un Base64 que embebe nonce + tag).
     */
    String encrypt(String plaintext);

    /** Descifra lo que {@link #encrypt(String)} produjo. */
    String decrypt(String ciphertext);
}
