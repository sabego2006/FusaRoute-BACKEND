package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidRegistrationException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.PasswordPolicy;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.RegisterUserCommand;
import com.fusaroute.domain.port.in.RegisterUserUseCase;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementacion del registro de usuarios. Sin anotaciones de Spring: recibe sus
 * dos puertos por constructor, asi que se puede probar con mocks y sin levantar
 * el contexto. El bean lo arma {@code UseCaseConfig} en infraestructura.
 */
public class RegisterUserService implements RegisterUserUseCase {

    private static final int NAME_MAX = 120;
    private static final String NAME_FIELD = "name";

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;

    public RegisterUserService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public User register(RegisterUserCommand command) {
        // 1. Validar los tres campos acumulando incumplimientos, para devolverlos
        //    juntos en un solo 400.
        List<FieldViolation> violations = new ArrayList<>();
        validateName(command.name(), violations);
        Email.validate(command.email()).ifPresent(violations::add);
        violations.addAll(PasswordPolicy.validate(command.password()));
        if (!violations.isEmpty()) {
            throw new InvalidRegistrationException(violations);
        }

        Email email = Email.of(command.email());

        // 2. Si ya existe el correo, cortar aqui: no se hashea nada.
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        // 3. Hashear, crear el usuario (rol USER, active=true) y guardar. La
        //    contrasena en claro no sale de este metodo ni se loguea.
        String passwordHash = passwordHasher.hash(command.password());
        User user = User.register(command.name().trim(), email, passwordHash);
        return userRepository.save(user);
    }

    private void validateName(String name, List<FieldViolation> violations) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            violations.add(new FieldViolation(NAME_FIELD, "El nombre es obligatorio"));
        } else if (trimmed.length() > NAME_MAX) {
            violations.add(new FieldViolation(NAME_FIELD, "El nombre supera los " + NAME_MAX + " caracteres"));
        }
    }
}
