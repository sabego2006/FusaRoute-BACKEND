package com.fusaroute.infrastructure.config;

import com.fusaroute.application.usecase.ChangePasswordService;
import com.fusaroute.application.usecase.GetProfileService;
import com.fusaroute.application.usecase.GetRouteDetailService;
import com.fusaroute.application.usecase.ListActiveRoutesService;
import com.fusaroute.application.usecase.LoginService;
import com.fusaroute.application.usecase.RegisterUserService;
import com.fusaroute.application.usecase.UpdateProfileService;
import com.fusaroute.domain.port.in.ChangePasswordUseCase;
import com.fusaroute.domain.port.in.GetProfileUseCase;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.LoginUseCase;
import com.fusaroute.domain.port.in.RegisterUserUseCase;
import com.fusaroute.domain.port.in.UpdateProfileUseCase;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.TokenIssuerPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Cablea los casos de uso con sus adaptadores. Al vivir aqui, en infraestructura,
 * la capa {@code application} queda libre de anotaciones de Spring y sus tests no
 * necesitan levantar el contexto.
 */
@Configuration
public class UseCaseConfig {

    /**
     * Reloj para decidir que tarifa esta vigente "hoy". Se fija a America/Bogota
     * porque el backend corre en Render (UTC): pasadas las 19:00 en Colombia, la
     * fecha UTC ya seria la de manana y una tarifa que empieza manana se mostraria
     * un dia antes.
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }

    @Bean
    public ListActiveRoutesUseCase listActiveRoutesUseCase(RouteRepositoryPort routeRepository, Clock clock) {
        return new ListActiveRoutesService(routeRepository, clock);
    }

    @Bean
    public GetRouteDetailUseCase getRouteDetailUseCase(RouteRepositoryPort routeRepository, Clock clock) {
        return new GetRouteDetailService(routeRepository, clock);
    }

    @Bean
    public RegisterUserUseCase registerUserUseCase(UserRepositoryPort userRepository,
                                                   PasswordHasherPort passwordHasher) {
        return new RegisterUserService(userRepository, passwordHasher);
    }

    @Bean
    public LoginUseCase loginUseCase(UserRepositoryPort userRepository,
                                     PasswordHasherPort passwordHasher,
                                     TokenIssuerPort tokenIssuer) {
        return new LoginService(userRepository, passwordHasher, tokenIssuer);
    }

    @Bean
    public GetProfileUseCase getProfileUseCase(UserRepositoryPort userRepository) {
        return new GetProfileService(userRepository);
    }

    @Bean
    public UpdateProfileUseCase updateProfileUseCase(UserRepositoryPort userRepository) {
        return new UpdateProfileService(userRepository);
    }

    @Bean
    public ChangePasswordUseCase changePasswordUseCase(UserRepositoryPort userRepository,
                                                        PasswordHasherPort passwordHasher) {
        return new ChangePasswordService(userRepository, passwordHasher);
    }
}
