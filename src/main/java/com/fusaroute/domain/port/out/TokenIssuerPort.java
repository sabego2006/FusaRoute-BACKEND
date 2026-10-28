package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;

import java.time.Duration;

/**
 * Puerto de salida que firma los tokens de acceso de un usuario. El dominio
 * decide cuanto duran; el formato concreto (JWT) vive en un adaptador de
 * infraestructura.
 *
 * Hay dos clases de token (SCRUM-179):
 * <ul>
 *   <li>el <b>completo</b> (scope=full) emitido por {@link #issue(User, Duration)}
 *       y que vale 7 dias, llave de toda la API;</li>
 *   <li>el <b>de enrolamiento</b> (scope=totp_enroll) emitido por
 *       {@link #issueEnrollmentToken(User, Duration)}, de vida corta y que SOLO
 *       alcanza los endpoints de enrolar y confirmar TOTP.</li>
 * </ul>
 */
public interface TokenIssuerPort {

    IssuedToken issue(User user, Duration validity);

    IssuedToken issueEnrollmentToken(User user, Duration validity);
}
