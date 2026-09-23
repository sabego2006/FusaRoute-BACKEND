package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.CreateUserUseCasePort;
import com.fusaroute.domain.port.out.PasswordHasher;
import com.fusaroute.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Implementación del caso de uso para la creación de usuarios.
 * Orquestra el dominio y los puertos de salida para cumplir con RF-01.
 */
@Service
public class CreateUserUseCase implements CreateUserUseCasePort {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    // Regex para: mínimo 8 caracteres, al menos 1 mayúscula y 1 número
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[A-Z]).{8,}$");

    public CreateUserUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public User createUser(String name, String email, String password) {
        // 1. Validar formato de email mediante Value Object
        Email validatedEmail = Email.of(email);

        // 2. Validar unicidad del correo electrónico
        if (userRepository.existsByEmail(validatedEmail.getValue())) {
            throw new IllegalArgumentException("El correo electrónico ya se encuentra registrado.");
        }

        // 3. Validar fortaleza de la contraseña
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres, incluyendo una mayúscula y un número.");
        }

        // 4. Hashear la contraseña utilizando el puerto de seguridad
        String hashedPassword = passwordHasher.hash(password);

        // 5. Crear la entidad de dominio y activarla
        User user = new User(name, validatedEmail.getValue(), hashedPassword);
        user.activate();

        // 6. Persistir el usuario
        return userRepository.save(user);
    }
}
