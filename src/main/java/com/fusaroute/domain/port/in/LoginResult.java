package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;

/** Resultado de un inicio de sesion exitoso: el usuario autenticado y su token. */
public record LoginResult(User user, IssuedToken token) {
}
