package com.fusaroute.application.usecase;

import com.fusaroute.application.dto.LoginRequest;
import com.fusaroute.application.dto.LoginResponse;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.out.PasswordHasher;
import com.fusaroute.domain.port.out.UserRepository;
import com.fusaroute.domain.port.out.TokenServicePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Caso de uso para la autenticación de usuarios.
 * Orquestra la validación de credenciales y la generación del token.
 */
@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenServicePort tokenServicePort;

    public LoginUseCase(UserRepository userRepository, PasswordHasher passwordHasher, TokenServicePort tokenServicePort) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenServicePort = tokenServicePort;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. Buscar usuario por email
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas."));

        // 2. Verificar bloqueo por intentos fallidos
        if (user.isLockedOut()) {
            throw new IllegalArgumentException("Cuenta bloqueada temporalmente debido a demasiados intentos fallidos.");
        }

        // 3. Verificar que la contraseña sea correcta
        if (!passwordHasher.verify(request.password(), user.getPassword())) {
            user.incrementFailedAttempts();

            if (user.getFailedLoginAttempts() >= 5) {
                user.setLockout(15); // Bloqueo por 15 minutos
            }

            userRepository.updateLockoutStatus(user.getId(), user.getFailedLoginAttempts(), user.getLockoutUntil());

            // Mensaje genérico para los primeros 4 intentos, explícito al 5to
            if (user.getFailedLoginAttempts() < 5) {
                throw new IllegalArgumentException("Credenciales inválidas.");
            } else {
                throw new IllegalArgumentException("Cuenta bloqueada temporalmente debido a demasiados intentos fallidos.");
            }
        }

        // 4. Login exitoso: resetear intentos fallidos
        user.resetFailedAttempts();
        userRepository.updateLockoutStatus(user.getId(), 0, null);

        // 5. Generar token de acceso
        String token = tokenServicePort.generateToken(user);

        return new LoginResponse(token);
    }
}
