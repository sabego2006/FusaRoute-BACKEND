package com.fusaroute.domain.port.in;

/**
 * Puerto de entrada del paso 2 del enrolamiento TOTP (SCRUM-179): verifica el
 * primer codigo del usuario y, si cuadra, activa el segundo factor, genera y
 * guarda los codigos de respaldo (hasheados) y emite el JWT completo de 7 dias.
 *
 * @throws com.fusaroute.domain.exception.InvalidTotpException codigo incorrecto.
 * @throws com.fusaroute.domain.exception.TotpAlreadyEnabledException ya esta activo.
 */
public interface ConfirmTotpUseCase {

    ConfirmTotpResult confirm(ConfirmTotpCommand command);
}
