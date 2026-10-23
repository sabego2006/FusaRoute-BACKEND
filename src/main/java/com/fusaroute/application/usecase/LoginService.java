package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidCredentialsException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.LoginCommand;
import com.fusaroute.domain.port.in.LoginResult;
import com.fusaroute.domain.port.in.LoginUseCase;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.TokenIssuerPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;

import java.time.Duration;
import java.util.Optional;

/**
 * Implementacion del inicio de sesion. Sin anotaciones de Spring: recibe sus tres
 * puertos por constructor, asi que se prueba con mocks y sin levantar el contexto.
 * El bean lo arma {@code UseCaseConfig} en infraestructura.
 *
 * Todo motivo de rechazo lanza la MISMA {@link InvalidCredentialsException}.
 * Cuenta los intentos fallidos de un usuario existente (SCRUM-36) y los reinicia
 * al iniciar sesion bien. El bloqueo temporal al quinto intento sigue fuera de
 * este caso de uso: es SCRUM-37 (Sprint 4), dentro de SCRUM-155.
 */
public class LoginService implements LoginUseCase {

    // RNF-04: maximo un login por semana; vencido el token, el usuario reloguea.
    static final Duration TOKEN_VALIDITY = Duration.ofDays(7);

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenIssuerPort tokenIssuer;

    public LoginService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher,
                        TokenIssuerPort tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
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

        // 3. Solo una contrasena errada de una cuenta que existe cuenta como intento
        //    fallido: es el vector de fuerza bruta. Un correo inexistente no tiene
        //    fila donde contar, y el rechazo sigue siendo identico hacia afuera.
        if (found.isPresent() && !passwordMatches) {
            userRepository.save(found.get().registerFailedLogin());
        }

        if (found.isEmpty() || !passwordMatches || !found.get().isActive()) {
            throw new InvalidCredentialsException();
        }

        User user = found.get();
        if (user.getFailedLoginAttempts() > 0) {
            user = user.resetFailedLogins();
            userRepository.save(user);
        }
        IssuedToken token = tokenIssuer.issue(user, TOKEN_VALIDITY);
        return new LoginResult(user, token);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
