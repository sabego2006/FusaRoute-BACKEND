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
 * El contador de intentos fallidos y el bloqueo temporal quedaron fuera de este
 * caso de uso a proposito: se movieron a SCRUM-155 (Sprint 3).
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

        if (found.isEmpty() || !passwordMatches || !found.get().isActive()) {
            throw new InvalidCredentialsException();
        }

        User user = found.get();
        IssuedToken token = tokenIssuer.issue(user, TOKEN_VALIDITY);
        return new LoginResult(user, token);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
