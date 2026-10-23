package com.fusaroute.domain.port.in;

/**
 * Puerto de entrada del paso 1 del enrolamiento TOTP (SCRUM-179): genera un
 * secreto para el usuario y devuelve el URI/QR para que su app autenticadora lo
 * registre. El segundo factor NO queda activo hasta que el usuario confirme con
 * un codigo valido ({@link ConfirmTotpUseCase}).
 *
 * Idempotente dentro del mismo "no activado": si el usuario llama varias veces,
 * cada vez reemplaza el secreto pendiente. Si el usuario ya tiene TOTP activo,
 * lanza {@link com.fusaroute.domain.exception.TotpAlreadyEnabledException} —
 * el re-enrolamiento tras cambiar de telefono se expresa borrando y volviendo
 * a enrolar, no reusando este metodo con un flag.
 */
public interface EnrollTotpUseCase {

    TotpEnrollmentResult enroll(Long userId);
}
