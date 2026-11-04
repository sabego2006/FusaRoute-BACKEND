package com.fusaroute.domain.port.in;

/**
 * Puerto de entrada del paso 1 del enrolamiento TOTP (SCRUM-179): genera un
 * secreto para el usuario y devuelve el URI/QR para que su app autenticadora lo
 * registre. El segundo factor NO queda activo hasta que el usuario confirme con
 * un codigo valido ({@link ConfirmTotpUseCase}).
 *
 * Idempotente dentro del mismo "no activado": si el usuario llama varias veces,
 * cada vez reemplaza el secreto pendiente.
 *
 * Si el usuario YA tiene TOTP activo, el resultado depende de
 * {@link EnrollTotpCommand#allowReset()}: sin permiso lanza
 * {@link com.fusaroute.domain.exception.TotpAlreadyEnabledException}; con
 * permiso borra el factor anterior y arranca uno nuevo. Esa es la via del
 * cambio de telefono, y es un solo viaje al servidor: partirla en
 * "apagar" + "enrolar" dejaria al usuario sin segundo factor si la segunda
 * llamada falla.
 */
public interface EnrollTotpUseCase {

    TotpEnrollmentResult enroll(EnrollTotpCommand command);
}
