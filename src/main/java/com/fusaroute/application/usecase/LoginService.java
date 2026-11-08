package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.AccountLockedException;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * al iniciar sesion bien.
 *
 * SCRUM-155 agrega el bloqueo temporal: al quinto intento fallido consecutivo
 * la cuenta queda bloqueada {@link com.fusaroute.domain.model.User#LOCK_DURATION}
 * y la peticion termina en {@link AccountLockedException} — un aviso ESPECIFICO,
 * distinto del generico de {@link InvalidCredentialsException} — en vez del 401
 * de siempre. Mientras la cuenta este bloqueada, cualquier intento (aunque la
 * contrasena sea correcta) recibe el mismo aviso de bloqueo, sin llegar a
 * verificar la contrasena. Si la ventana ya expiro, el intento la limpia y
 * reinicia el contador antes de evaluar credenciales, como si el bloqueo nunca
 * hubiera existido.
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
    private final Clock clock;

    public LoginService(UserRepositoryPort userRepository,
                        PasswordHasherPort passwordHasher,
                        TokenIssuerPort tokenIssuer,
                        TotpPort totp,
                        RecoveryCodePort recoveryCodes,
                        RecoveryCodeRepositoryPort recoveryCodeRepository,
                        Clock clock) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.clock = clock;
    }

    @Override
    public LoginResult login(LoginCommand command) {
        // 1. Un correo con formato invalido o una contrasena vacia nunca pueden
        //    coincidir con una cuenta: se rechaza igual que cualquier otra
        //    credencial mala, sin un 400 aparte que revele reglas de validacion.
        if (Email.validate(command.email()).isPresent() || isBlank(command.password())) {
            throw new InvalidCredentialsException();
        }

        Instant now = clock.instant();
        Optional<User> found = userRepository.findByEmail(Email.of(command.email()));

        // 1.5. Bloqueo temporal (SCRUM-155). Si la cuenta esta bloqueada, el
        //      intento ni siquiera verifica la contrasena: el aviso es el mismo
        //      para cualquier intento dentro de la ventana. Si el bloqueo ya
        //      expiro, se limpia y el contador vuelve a cero antes de evaluar,
        //      como si nunca hubiera existido.
        if (found.isPresent()) {
            User candidate = found.get();
            if (candidate.isLocked(now)) {
                throw new AccountLockedException();
            }
            if (candidate.getLockedUntil() != null) {
                candidate = candidate.clearLockAndResetAttempts();
                userRepository.save(candidate);
                found = Optional.of(candidate);
            }
        }

        // 2. La verificacion se ejecuta SIEMPRE, aunque el correo no exista: con
        //    hash null el puerto hace el mismo trabajo de BCrypt y devuelve false,
        //    para que el tiempo de respuesta no delate que correos tienen cuenta.
        boolean passwordMatches = passwordHasher.matches(
                command.password(), found.map(User::getPasswordHash).orElse(null));

        // 3. Solo una contrasena errada de una cuenta que existe cuenta como
        //    intento fallido: es el vector de fuerza bruta. Un correo inexistente
        //    no tiene fila donde contar, y el rechazo sigue siendo identico hacia
        //    afuera. Si este intento alcanza el quinto fallo, la cuenta queda
        //    bloqueada y el aviso es el especifico de bloqueo, no el generico.
        if (found.isPresent() && !passwordMatches) {
            User afterFailure = found.get().registerFailedLogin(now);
            userRepository.save(afterFailure);
            if (afterFailure.isLocked(now)) {
                throw new AccountLockedException();
            }
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
        //    hasta el limite del contador — y si este codigo es el que lo
        //    alcanza, el aviso pasa a ser el de bloqueo (SCRUM-155), no el de
        //    codigo invalido.
        String code = command.totpCode().trim();
        boolean totpOk = totp.verifyCode(user.getTotpSecret(), code);
        boolean recoveryOk = !totpOk && tryConsumeRecoveryCode(user.getId(), code);

        if (!totpOk && !recoveryOk) {
            User afterFailure = user.registerFailedLogin(now);
            userRepository.save(afterFailure);
            if (afterFailure.isLocked(now)) {
                throw new AccountLockedException();
            }
            throw new InvalidTotpException();
        }

        // SCRUM-101: el ingreso exitoso se GUARDA SIEMPRE, aunque no hubiera
        // intentos fallidos que reiniciar. Antes solo se guardaba para limpiar el
        // contador; ahora la fila tambien lleva el instante de este ingreso y el del
        // anterior, que es lo que el contador de respuestas nuevas de RF-14 compara.
        // Sin este guardado incondicional, un usuario que nunca falla la contrasena
        // no tendria registro de ingresos y su contador quedaria siempre en cero.
        user = user.registerSuccessfulLogin(now);
        userRepository.save(user);
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
