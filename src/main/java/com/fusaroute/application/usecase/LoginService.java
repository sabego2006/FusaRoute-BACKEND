package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidCredentialsException;
import com.fusaroute.domain.exception.InvalidTotpException;
import com.fusaroute.domain.exception.TotpEnrollmentRequiredException;
import com.fusaroute.domain.exception.TotpRequiredException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.RecoveryCode;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.LoginCommand;
import com.fusaroute.domain.port.in.LoginResult;
import com.fusaroute.domain.port.in.LoginUseCase;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.RecoveryCodePort;
import com.fusaroute.domain.port.out.RecoveryCodeRepositoryPort;
import com.fusaroute.domain.port.out.TokenIssuerPort;
import com.fusaroute.domain.port.out.TotpPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion del inicio de sesion. Sin anotaciones de Spring: recibe sus
 * puertos por constructor, asi que se prueba con mocks y sin levantar el contexto.
 * El bean lo arma {@code UseCaseConfig} en infraestructura.
 *
 * Todo motivo de rechazo "clasico" (correo malo, contrasena mala, cuenta
 * inactiva) sigue lanzando la MISMA {@link InvalidCredentialsException}; eso NO
 * cambia con SCRUM-179.
 *
 * SCRUM-179 agrega el segundo factor TOTP, obligatorio. Despues de validar la
 * contrasena el flujo se bifurca:
 * <ul>
 *   <li>Usuario SIN TOTP activo: la cuenta esta a medias, nadie puede entrar.
 *       Se emite un {@code enrollmentToken} de 10 min con alcance acotado y se
 *       lanza {@link TotpEnrollmentRequiredException} con el, para que el
 *       frontend arranque el flujo de enrolamiento forzado.</li>
 *   <li>Usuario CON TOTP activo pero sin codigo en la peticion:
 *       {@link TotpRequiredException}. El frontend muestra el paso 2.</li>
 *   <li>Codigo presente: se verifica como TOTP; si falla, se intenta como
 *       codigo de respaldo (un solo uso); si ambos fallan,
 *       {@link InvalidTotpException} y se cuenta el intento (reusa SCRUM-36).</li>
 * </ul>
 *
 * Cuenta los intentos fallidos de un usuario existente (SCRUM-36) y los reinicia
 * al iniciar sesion bien. El bloqueo temporal al quinto intento sigue fuera de
 * este caso de uso: es SCRUM-37 (Sprint 4), dentro de SCRUM-155.
 */
public class LoginService implements LoginUseCase {

    // RNF-04: maximo un login por semana; vencido el token, el usuario reloguea.
    static final Duration TOKEN_VALIDITY = Duration.ofDays(7);

    // Vida del token de enrolamiento (SCRUM-179): corta por diseno. Alcance
    // acotado por el claim scope=totp_enroll; con la contrasena ya validada,
    // 10 minutos son suficientes para abrir la app autenticadora y confirmar.
    static final Duration ENROLLMENT_TOKEN_VALIDITY = Duration.ofMinutes(10);

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenIssuerPort tokenIssuer;
    private final TotpPort totp;
    private final RecoveryCodePort recoveryCodes;
    private final RecoveryCodeRepositoryPort recoveryCodeRepository;

    public LoginService(UserRepositoryPort userRepository,
                        PasswordHasherPort passwordHasher,
                        TokenIssuerPort tokenIssuer,
                        TotpPort totp,
                        RecoveryCodePort recoveryCodes,
                        RecoveryCodeRepositoryPort recoveryCodeRepository) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    @Override
    public LoginResult login(LoginCommand command) {
        // 1. Un correo con formato invalido o una contrasena vacia nunca pueden
        //    coincidir con una cuenta: se rechaza igual que cualquier otra
        //    credencial mala, sin un 400 aparte que revele reglas de validacion.
        if (Email.validate(command.email()).isPresent() || isBlank(command.password())) {
            throw new InvalidCredentialsException();
        }

        Optional<User> found = userRepository.findByEmail(Email.of(command.email()));

        // 2. La verificacion se ejecuta SIEMPRE, aunque el correo no exista: con
        //    hash null el puerto hace el mismo trabajo de BCrypt y devuelve false,
        //    para que el tiempo de respuesta no delate que correos tienen cuenta.
        boolean passwordMatches = passwordHasher.matches(
                command.password(), found.map(User::getPasswordHash).orElse(null));

        // 3. Solo una contrasena errada de una cuenta que existe cuenta como
        //    intento fallido: es el vector de fuerza bruta. Un correo inexistente
        //    no tiene fila donde contar, y el rechazo sigue siendo identico hacia
        //    afuera.
        if (found.isPresent() && !passwordMatches) {
            userRepository.save(found.get().registerFailedLogin());
        }

        if (found.isEmpty() || !passwordMatches || !found.get().isActive()) {
            throw new InvalidCredentialsException();
        }

        User user = found.get();

        // 4. Segundo factor (SCRUM-179). Si el usuario aun no enrolo, se le
        //    entrega un token de enrolamiento y la peticion termina en 401 con
        //    pista; el frontend lo redirige al flujo forzado. El reset del
        //    contador NO se hace aqui: eso es un login exitoso de verdad, y aun
        //    no lo es — el token de enrolamiento no da acceso a la API.
        if (!user.isTotpEnabled()) {
            IssuedToken enrollmentToken = tokenIssuer.issueEnrollmentToken(user, ENROLLMENT_TOKEN_VALIDITY);
            throw new TotpEnrollmentRequiredException(enrollmentToken);
        }

        // 5. TOTP activo pero sin codigo: no hay nada que verificar.
        if (isBlank(command.totpCode())) {
            throw new TotpRequiredException();
        }

        // 6. Codigo presente. Primero se prueba como TOTP; si no, como codigo de
        //    respaldo (consumo en el acto). Falla el segundo factor =>
        //    InvalidTotpException y el intento cuenta (reusa SCRUM-36). Un
        //    atacante que ya tenga la contrasena solo puede intentar codigos
        //    hasta el limite del contador.
        String code = command.totpCode().trim();
        boolean totpOk = totp.verifyCode(user.getTotpSecret(), code);
        boolean recoveryOk = !totpOk && tryConsumeRecoveryCode(user.getId(), code);

        if (!totpOk && !recoveryOk) {
            userRepository.save(user.registerFailedLogin());
            throw new InvalidTotpException();
        }

        if (user.getFailedLoginAttempts() > 0) {
            user = user.resetFailedLogins();
            userRepository.save(user);
        }
        IssuedToken token = tokenIssuer.issue(user, TOKEN_VALIDITY);
        return new LoginResult(user, token);
    }

    /**
     * Busca un codigo de respaldo activo que encaje con lo que envio el usuario
     * y lo marca como consumido. Es resistente a hashes nulos: no explota si no
     * hay respaldos registrados.
     */
    private boolean tryConsumeRecoveryCode(Long userId, String plain) {
        List<RecoveryCode> active = recoveryCodeRepository.findActiveByUser(userId);
        for (RecoveryCode candidate : active) {
            if (recoveryCodes.matches(plain, candidate.getCodeHash())) {
                recoveryCodeRepository.markUsed(candidate);
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
