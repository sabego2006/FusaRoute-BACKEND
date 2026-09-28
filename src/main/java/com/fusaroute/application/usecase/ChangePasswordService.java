package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.IncorrectCurrentPasswordException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.ChangePasswordCommand;
import com.fusaroute.domain.port.in.ChangePasswordUseCase;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class ChangePasswordService implements ChangePasswordUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;

    public ChangePasswordService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void execute(ChangePasswordCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        // 1. Validar contraseña actual
        if (!passwordHasher.matches(command.currentPassword(), user.getPasswordHash())) {
            throw new IncorrectCurrentPasswordException();
        }

        // 2. Validar nueva contraseña (siguiendo política de negocio)
        if (command.newPassword() == null || command.newPassword().length() < 8) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres.");
        }

        // 3. Hashear y persistir
        String newHash = passwordHasher.hash(command.newPassword());
        User updatedUser = user.withPasswordHash(newHash);

        userRepository.save(updatedUser);
    }
}
