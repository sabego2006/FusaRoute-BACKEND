package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidProfileException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.NameRule;
import com.fusaroute.domain.model.PhoneNumber;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.UpdateProfileCommand;
import com.fusaroute.domain.port.in.UpdateProfileUseCase;
import com.fusaroute.domain.port.out.UserRepositoryPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Actualizacion del perfil (RF-03, PUT /api/users/me). Reutiliza las reglas de
 * validacion de dominio ({@link NameRule}, {@link Email#validate},
 * {@link PhoneNumber#validate}) y mantiene el 409 si el nuevo correo ya existe.
 */
public class UpdateProfileService implements UpdateProfileUseCase {

    private final UserRepositoryPort userRepository;

    public UpdateProfileService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User updateProfile(UpdateProfileCommand command) {
        // 1. Validar campos acumulando incumplimientos.
        List<FieldViolation> violations = new ArrayList<>();
        NameRule.validate(command.name()).ifPresent(violations::add);
        Email.validate(command.email()).ifPresent(violations::add);
        PhoneNumber.validate(command.phone()).ifPresent(violations::add);

        if (!violations.isEmpty()) {
            throw new InvalidProfileException(violations);
        }

        // 2. Buscar el usuario existente.
        User existing = userRepository.findById(command.userId())
                .orElseThrow(UserNotFoundException::new);

        Email newEmail = Email.of(command.email());

        // 3. Si el correo cambio, verificar que no este en uso por otro.
        if (!existing.getEmail().equals(newEmail) && userRepository.existsByEmail(newEmail)) {
            throw new com.fusaroute.domain.exception.EmailAlreadyRegisteredException();
        }

        // 4. Actualizar y guardar.
        User updated = existing.updateProfile(
                command.name().trim(),
                newEmail,
                PhoneNumber.normalize(command.phone()));
        return userRepository.save(updated);
    }
}
