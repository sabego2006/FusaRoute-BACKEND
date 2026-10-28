package com.fusaroute.domain.port.in;

/**
 * Puerto de entrada que devuelve si el usuario ya tiene activo el segundo
 * factor (SCRUM-179). Lo consume la pantalla de perfil del frontend para
 * decidir si mostrar "activar" o "re-enrolar".
 */
public interface GetTotpStatusUseCase {

    boolean isEnabled(Long userId);
}
