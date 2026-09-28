package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.IncorrectCurrentPasswordException;
import com.fusaroute.domain.exception.InvalidProfileException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.PasswordPolicy;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.ChangePasswordCommand;
import com.fusaroute.domain.port.in.ChangePasswordUseCase;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;

import java.util.List;

/**
 * Cambio de contrasena (RF-03, PUT /api/users/me/password). Exige la contrasena
 * actual como verificacion y valida la nueva con la misma
 * {@link PasswordPolicy} del registro.
 */
public class ChangePasswordService implements ChangePasswordUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;

    public ChangePasswordService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(UserNotFoundException::new);

        // 1. Verificar la contrasena actual.
        if (!passwordHasher.matches(command.currentPassword(), user.getPasswordHash())) {
            throw new IncorrectCurrentPasswordException();
        }

        // 2. Validar la nueva contrasena con la misma politica del registro.
        List<FieldViolation> violations = PasswordPolicy.validate(command.newPassword());
        if (!violations.isEmpty()) {
            throw new InvalidProfileException(violations);
        }

        // 3. Hashear y guardar.
        String newHash = passwordHasher.hash(command.newPassword());
        User updated = user.withPasswordHash(newHash);
        userRepository.save(updated);
    }
}
