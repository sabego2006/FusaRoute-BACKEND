package com.fusaroute.infrastructure.config;

import com.fusaroute.application.usecase.AdminFeedbackService;
import com.fusaroute.application.usecase.ChangePasswordService;
import com.fusaroute.application.usecase.ConfirmTotpService;
import com.fusaroute.application.usecase.EnrollTotpService;
import com.fusaroute.application.usecase.FavoriteDestinationService;
import com.fusaroute.application.usecase.FeedbackService;
import com.fusaroute.application.usecase.GetCongestionWarningService;
import com.fusaroute.application.usecase.GetProfileService;
import com.fusaroute.application.usecase.GetRouteDetailService;
import com.fusaroute.application.usecase.GetRouteFareService;
import com.fusaroute.application.usecase.GetTotpStatusService;
import com.fusaroute.application.usecase.ListActiveRoutesService;
import com.fusaroute.application.usecase.ListPlacesService;
import com.fusaroute.application.usecase.LoginService;
import com.fusaroute.application.usecase.RegenerateRecoveryCodesService;
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
import com.fusaroute.domain.port.in.GetTotpStatusUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.ListPlacesUseCase;
import com.fusaroute.domain.port.in.LoginUseCase;
import com.fusaroute.domain.port.in.RegenerateRecoveryCodesUseCase;
import com.fusaroute.domain.port.in.RegisterUserUseCase;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import com.fusaroute.domain.port.in.UpdateProfileUseCase;
import com.fusaroute.domain.port.out.FavoriteDestinationRepositoryPort;
import com.fusaroute.domain.port.out.FeedbackRepositoryPort;
import com.fusaroute.domain.port.out.ForbiddenWordsPort;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import com.fusaroute.domain.port.out.RecoveryCodePort;
import com.fusaroute.domain.port.out.RecoveryCodeRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.RouteSpeedPort;
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
    public GetCongestionWarningUseCase getCongestionWarningUseCase(
            TrafficSectorRepositoryPort trafficSectorRepository, Clock clock) {
        return new GetCongestionWarningService(trafficSectorRepository, clock);
    }

    @Bean
    public SearchRouteUseCase searchRouteUseCase(RouteRepositoryPort routeRepository,
                                                  PlaceRepositoryPort placeRepository,
                                                  TravelTimePort travelTimePort,
                                                  TrafficSectorRepositoryPort trafficSectorRepository,
                                                  RouteSpeedPort routeSpeedPort,
                                                  MeterRegistry meterRegistry, Clock clock) {
        return new SearchRouteService(routeRepository, placeRepository, travelTimePort,
                trafficSectorRepository, routeSpeedPort, meterRegistry, clock);
    }

    @Bean
    public ListPlacesUseCase listPlacesUseCase(PlaceRepositoryPort placeRepository,
                                               RouteRepositoryPort routeRepository) {
        return new ListPlacesService(placeRepository, routeRepository);
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

    /**
     * Unico bean del destino favorito (RF-10, SCRUM-21). Devuelve el tipo concreto y
     * no una interfaz porque {@code FavoriteDestinationService} implementa tres casos
     * de uso: declararlo como una sola interfaz dejaria las otras dos sin bean, y
     * declararlo tres veces crearia tres instancias de la misma cosa.
     */
    @Bean
    public FavoriteDestinationService favoriteDestinationService(
            FavoriteDestinationRepositoryPort favoriteDestinationRepository, Clock clock) {
        return new FavoriteDestinationService(favoriteDestinationRepository, clock);
    }

    /**
     * Unico bean de la caja de comentarios (RF-08, SCRUM-22). Devuelve el tipo
     * concreto y no una interfaz por la misma razon que el historial y el favorito:
     * {@code FeedbackService} implementa seis casos de uso, y declararlo como una
     * sola interfaz dejaria las otras cinco sin bean.
     *
     * Entre esos seis esta {@code CountPendingFeedbackUseCase}, que es la metrica 4
     * del panel de RF-13 (SCRUM-26): el panel llama al caso de uso, no al
     * repositorio, para que su "pendiente" sea por construccion el mismo estado que
     * bloquea la edicion de un comentario.
     */
    @Bean
    public FeedbackService feedbackService(FeedbackRepositoryPort feedbackRepository,
                                           UserRepositoryPort userRepository,
                                           ForbiddenWordsPort forbiddenWords,
                                           Clock clock) {
        return new FeedbackService(feedbackRepository, userRepository, forbiddenWords, clock);
    }

    /**
     * Comentarios desde el lado del administrador (RF-14, SCRUM-23). Bean aparte del
     * de la caja de comentarios porque es una clase aparte, y lo es porque su
     * autorizacion es distinta: ver el javadoc de {@code AdminFeedbackService}.
     */
    @Bean
    public AdminFeedbackService adminFeedbackService(FeedbackRepositoryPort feedbackRepository,
                                                      Clock clock) {
        return new AdminFeedbackService(feedbackRepository, clock);
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
                                     RecoveryCodeRepositoryPort recoveryCodeRepository,
                                     Clock clock) {
        return new LoginService(userRepository, passwordHasher, tokenIssuer, totp,
                recoveryCodes, recoveryCodeRepository, clock);
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
                                                 TokenIssuerPort tokenIssuer,
                                                 Clock clock) {
        return new ConfirmTotpService(userRepository, totp, recoveryCodes,
                recoveryCodeRepository, tokenIssuer, clock);
    }

    @Bean
    public GetTotpStatusUseCase getTotpStatusUseCase(UserRepositoryPort userRepository) {
        return new GetTotpStatusService(userRepository);
    }

    @Bean
    public RegenerateRecoveryCodesUseCase regenerateRecoveryCodesUseCase(
            UserRepositoryPort userRepository,
            RecoveryCodePort recoveryCodes,
            RecoveryCodeRepositoryPort recoveryCodeRepository) {
        return new RegenerateRecoveryCodesService(userRepository, recoveryCodes, recoveryCodeRepository);
    }
}
