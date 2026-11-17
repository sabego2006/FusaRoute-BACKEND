package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.User;

import java.time.Duration;

/**
 * Puerto de salida del dispositivo de confianza del segundo factor: un token
 * firmado y sin estado que, mientras sea valido, le permite a un USUARIO
 * entrar sin repetir el codigo TOTP (decision de producto del 17-nov).
 *
 * Nunca decide el rol: {@code LoginService} ya resolvio que la cuenta es
 * USER antes de llamar a este puerto. Un ADMIN jamas lo usa.
 */
public interface TrustTokenPort {

    /** Emite un token de confianza para el usuario, valido por {@code validity}. */
    String issue(User user, Duration validity);

    /**
     * True si {@code token} es un token de confianza vigente, firmado por este
     * backend, para exactamente este usuario, y la contrasena y el secreto TOTP
     * con los que se emitio siguen siendo los vigentes. Nunca lanza: un token
     * vencido, manipulado, de otro usuario o con firma ajena simplemente no es
     * valido.
     */
    boolean isValid(String token, User user);
}
