package com.fusaroute.infrastructure.config;

import com.fusaroute.application.usecase.ChangePasswordService;
import com.fusaroute.application.usecase.ConfirmTotpService;
import com.fusaroute.application.usecase.EnrollTotpService;
import com.fusaroute.application.usecase.GetCongestionWarningService;
import com.fusaroute.application.usecase.GetProfileService;
import com.fusaroute.application.usecase.GetRouteDetailService;
import com.fusaroute.application.usecase.GetRouteFareService;
import com.fusaroute.application.usecase.GetRouteSignsService;
import com.fusaroute.application.usecase.GetTotpStatusService;
import com.fusaroute.application.usecase.ListActiveRoutesService;
import com.fusaroute.application.usecase.LoginService;
import com.fusaroute.application.usecase.RegisterUserService;
import com.fusaroute.application.usecase.SearchHistoryService;
import com.fusaroute.application.usecase.SearchRouteService;
import com.fusaroute.application.usecase.UpdateProfileService;
import com.fusaroute.domain.port.in.ChangePasswordUseCase;
import com.fusaroute.domain.port.in.ConfirmTotpUseCase;
import com.fusaroute.domain.port.in.EnrollTotpUseCase;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetProfileUseCase;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.in.GetRouteSignsUseCase;
import com.fusaroute.domain.port.in.GetTotpStatusUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.LoginUseCase;
import com.fusaroute.domain.port.in.RegisterUserUseCase;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import com.fusaroute.domain.port.in.UpdateProfileUseCase;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.RecoveryCodePort;
import com.fusaroute.domain.port.out.RecoveryCodeRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.RouteSignRepositoryPort;
import com.fusaroute.domain.port.out.SearchHistoryRepositoryPort;
import com.fusaroute.domain.port.out.TokenIssuerPort;
import com.fusaroute.domain.port.out.TotpPort;
import com.fusaroute.domain.port.out.TrafficSectorRepositoryPort;
import com.fusaroute.domain.port.out.TravelTimePort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
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
    public GetRouteFareUseCase getRouteFareUseCase(RouteRepositoryPort routeRepository, Clock clock) {
        return new GetRouteFareService(routeRepository, clock);
    }

    @Bean
    public GetRouteSignsUseCase getRouteSignsUseCase(RouteSignRepositoryPort routeSignRepository) {
        return new GetRouteSignsService(routeSignRepository);
    }

    @Bean
    public GetCongestionWarningUseCase getCongestionWarningUseCase(
            TrafficSectorRepositoryPort trafficSectorRepository, Clock clock) {
        return new GetCongestionWarningService(trafficSectorRepository, clock);
    }

    @Bean
    public SearchRouteUseCase searchRouteUseCase(RouteRepositoryPort routeRepository,
                                                  TravelTimePort travelTimePort,
                                                  MeterRegistry meterRegistry) {
        return new SearchRouteService(routeRepository, travelTimePort, meterRegistry);
    }

    /**
     * Unico bean del historial (RF-07, SCRUM-20). Devuelve el tipo concreto y no una
     * interfaz —a diferencia del resto de este archivo— porque {@code SearchHistoryService}
     * implementa dos casos de uso: declararlo como una sola interfaz dejaria la otra
     * sin bean, y declararlo dos veces crearia dos instancias de la misma cosa.
     */
    @Bean
    public SearchHistoryService searchHistoryService(SearchHistoryRepositoryPort searchHistoryRepository,
                                                     Clock clock) {
        return new SearchHistoryService(searchHistoryRepository, clock);
    }

    @Bean
    public RegisterUserUseCase registerUserUseCase(UserRepositoryPort userRepository,
                                                   PasswordHasherPort passwordHasher) {
        return new RegisterUserService(userRepository, passwordHasher);
    }

    @Bean
    public LoginUseCase loginUseCase(UserRepositoryPort userRepository,
                                     PasswordHasherPort passwordHasher,
                                     TokenIssuerPort tokenIssuer,
                                     TotpPort totp,
                                     RecoveryCodePort recoveryCodes,
                                     RecoveryCodeRepositoryPort recoveryCodeRepository) {
        return new LoginService(userRepository, passwordHasher, tokenIssuer, totp,
                recoveryCodes, recoveryCodeRepository);
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

    @Bean
    public EnrollTotpUseCase enrollTotpUseCase(UserRepositoryPort userRepository,
                                               TotpPort totp,
                                               @Value("${app.totp.issuer:FusaRoute}") String issuer) {
        return new EnrollTotpService(userRepository, totp, issuer);
    }

    @Bean
    public ConfirmTotpUseCase confirmTotpUseCase(UserRepositoryPort userRepository,
                                                 TotpPort totp,
                                                 RecoveryCodePort recoveryCodes,
                                                 RecoveryCodeRepositoryPort recoveryCodeRepository,
                                                 TokenIssuerPort tokenIssuer) {
        return new ConfirmTotpService(userRepository, totp, recoveryCodes,
                recoveryCodeRepository, tokenIssuer);
    }

    @Bean
    public GetTotpStatusUseCase getTotpStatusUseCase(UserRepositoryPort userRepository) {
        return new GetTotpStatusService(userRepository);
    }
}
