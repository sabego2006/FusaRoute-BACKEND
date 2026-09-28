package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;

import java.time.Duration;

/**
 * Puerto de salida que firma el token de acceso de un usuario. El dominio decide
 * cuanto dura; el formato concreto (JWT) vive en un adaptador de infraestructura.
 */
public interface TokenIssuerPort {

    IssuedToken issue(User user, Duration validity);
}
