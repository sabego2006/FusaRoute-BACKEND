package com.fusaroute.domain.exception;

/**
 * La cuenta esta bloqueada temporalmente tras alcanzar
 * {@link com.fusaroute.domain.model.User#MAX_FAILED_ATTEMPTS} intentos fallidos
 * consecutivos (SCRUM-155). Se lanza tanto en el intento que provoca el bloqueo
 * (el quinto) como en cualquier intento posterior dentro de la ventana.
 *
 * Deliberadamente NO lleva el instante de desbloqueo: el aviso es generico para
 * no exponer al cliente cuanto falta, a diferencia de {@link TotpEnrollmentRequiredException}
 * que si transporta datos porque el frontend los necesita para continuar el flujo.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("Cuenta bloqueada temporalmente por intentos fallidos");
    }
}
