package com.fusaroute.infrastructure.config;

import com.fusaroute.application.usecase.RegisterUserService;
import com.fusaroute.domain.port.in.RegisterUserUseCase;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cablea los casos de uso con sus adaptadores. Al vivir aqui, en infraestructura,
 * la capa {@code application} queda libre de anotaciones de Spring y sus tests no
 * necesitan levantar el contexto.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public RegisterUserUseCase registerUserUseCase(UserRepositoryPort userRepository,
                                                   PasswordHasherPort passwordHasher) {
        return new RegisterUserService(userRepository, passwordHasher);
    }
}
