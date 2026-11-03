package com.fusaroute.domain.port.in;

/**
 * Datos del paso 1 del enrolamiento TOTP (SCRUM-179).
 *
 * {@code allowReset} decide que pasa cuando el usuario YA tiene el segundo
 * factor activo: con {@code false} se rechaza, con {@code true} se borra el
 * factor anterior y se arranca un enrolamiento nuevo (cambio de telefono).
 *
 * Quien lo decide es el adaptador web a partir del alcance del token: solo un
 * token completo puede resetear. El token de enrolamiento vive 10 minutos y se
 * emite antes de que exista el factor, asi que si pudiera resetear bastaria con
 * reusarlo dentro de esa ventana para borrar el factor recien confirmado.
 */
public record EnrollTotpCommand(Long userId, boolean allowReset) {
}
