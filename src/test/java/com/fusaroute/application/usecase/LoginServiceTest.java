package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidCredentialsException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.LoginCommand;
import com.fusaroute.domain.port.in.LoginResult;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.TokenIssuerPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String HASH = "$2a$12$hash";

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private TokenIssuerPort tokenIssuer;

    @InjectMocks
    private LoginService service;

    private static User user(boolean active) {
        return new User(7L, "Ana", Email.of("ana@x.co"), HASH, null, UserRole.USER, active);
    }

    @Test
    void credenciales_correctas_emiten_token_con_vigencia_de_siete_dias() {
        User user = user(true);
        IssuedToken token = new IssuedToken("jwt", Instant.parse("2026-10-04T00:00:00Z"));
        when(userRepository.findByEmail(new Email("ana@x.co"))).thenReturn(Optional.of(user));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(tokenIssuer.issue(user, Duration.ofDays(7))).thenReturn(token);

        LoginResult result = service.login(new LoginCommand("  Ana@X.CO ", "Abcdef12"));

        assertThat(result.user()).isSameAs(user);
        assertThat(result.token()).isSameAs(token);
    }

    @Test
    void contrasena_incorrecta_se_rechaza_y_no_emite_token() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user(true)));
        when(passwordHasher.matches("mala", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "mala")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void correo_inexistente_igual_verifica_contra_hash_nulo_y_se_rechaza() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginCommand("nadie@x.co", "Abcdef12")))
                .isInstanceOf(InvalidCredentialsException.class);

        // Guarda de temporizacion: aunque no exista el correo, se hace el trabajo de BCrypt.
        verify(passwordHasher).matches("Abcdef12", null);
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void el_mensaje_es_el_mismo_para_correo_inexistente_y_contrasena_incorrecta() {
        when(userRepository.findByEmail(new Email("nadie@x.co"))).thenReturn(Optional.empty());
        when(userRepository.findByEmail(new Email("ana@x.co"))).thenReturn(Optional.of(user(true)));
        when(passwordHasher.matches("mala", HASH)).thenReturn(false);
        when(passwordHasher.matches("mala", null)).thenReturn(false);

        String inexistente = messageOf(new LoginCommand("nadie@x.co", "mala"));
        String incorrecta = messageOf(new LoginCommand("ana@x.co", "mala"));

        assertThat(inexistente).isEqualTo(incorrecta);
    }

    @Test
    void usuario_inactivo_se_rechaza_aunque_la_contrasena_sea_correcta() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user(false)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "Abcdef12")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void correo_con_formato_invalido_no_toca_los_puertos() {
        assertThatThrownBy(() -> service.login(new LoginCommand("correo-malo", "Abcdef12")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordHasher);
        verify(tokenIssuer, never()).issue(any(), any());
    }

    @Test
    void contrasena_vacia_o_nula_no_toca_los_puertos() {
        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "   ")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", null)))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login(new LoginCommand(null, "Abcdef12")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordHasher);
    }

    private String messageOf(LoginCommand command) {
        try {
            service.login(command);
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
        throw new AssertionError("Se esperaba InvalidCredentialsException");
    }
}
