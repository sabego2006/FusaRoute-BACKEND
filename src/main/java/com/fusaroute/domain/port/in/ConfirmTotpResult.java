package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;

import java.util.List;

/**
 * Resultado de confirmar el enrolamiento TOTP (SCRUM-179).
 *
 * Trae tres cosas: el usuario con TOTP ya activo, el JWT completo de 7 dias
 * (confirmar = quedar logueado, sin un segundo relogin) y los codigos de
 * respaldo en claro. Esos codigos se muestran UNA sola vez al usuario y nunca
 * mas los conoce el servidor (solo guarda sus hashes).
 */
public record ConfirmTotpResult(User user, IssuedToken token, List<String> recoveryCodes) {
}
