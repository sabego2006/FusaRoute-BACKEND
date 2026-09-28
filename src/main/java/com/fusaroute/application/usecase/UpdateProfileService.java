package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.InvalidProfileException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.UpdateProfileCommand;
import com.fusaroute.domain.port.in.UpdateProfileUseCase;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UpdateProfileService implements UpdateProfileUseCase {

    private final UserRepositoryPort userRepository;

    public UpdateProfileService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User execute(UpdateProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new com.fusaroute.domain.exception.UserNotFoundException(command.userId()));

        // 1. Validaciones
        List<String> violations = new ArrayList<>();

        if (command.name() == null || command.name().trim().isEmpty()) {
            violations.add("El nombre es obligatorio.");
        }

        Email email = null;
        try {
            email = Email.of(command.email());
        } catch (IllegalArgumentException e) {
            violations.add("El formato del correo electrónico es inválido.");
        }

        if (!violations.isEmpty()) {
            throw new InvalidProfileException(violations);
        }

        // 2. Verificación de correo único si ha cambiado
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new EmailAlreadyRegisteredException();
            }
        }

        // 3. Actualización inmutable
        User updatedUser = user.updateProfile(command.name(), email, command.phone());

        return userRepository.save(updatedUser);
    }
}
