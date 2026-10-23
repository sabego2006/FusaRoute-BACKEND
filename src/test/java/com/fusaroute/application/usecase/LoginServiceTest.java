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
import org.mockito.ArgumentCaptor;
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
        return user(active, 0);
    }

    private static User user(boolean active, int failedAttempts) {
        return new User(7L, "Ana", Email.of("ana@x.co"), HASH, null, UserRole.USER, active,
                failedAttempts);
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

    @Test
    void contrasena_errada_de_usuario_existente_incrementa_el_contador() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user(true, 2)));
        when(passwordHasher.matches("mala", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "mala")))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFailedLoginAttempts()).isEqualTo(3);
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void correo_inexistente_no_incrementa_ni_guarda() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginCommand("nadie@x.co", "mala")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void usuario_inactivo_con_contrasena_correcta_no_cuenta_como_intento_fallido() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user(false)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "Abcdef12")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_exitoso_reinicia_el_contador_a_cero() {
        IssuedToken token = new IssuedToken("jwt", Instant.parse("2026-10-04T00:00:00Z"));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user(true, 3)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(tokenIssuer.issue(any(), any())).thenReturn(token);

        LoginResult result = service.login(new LoginCommand("ana@x.co", "Abcdef12"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFailedLoginAttempts()).isZero();
        assertThat(result.user().getFailedLoginAttempts()).isZero();
    }

    @Test
    void login_exitoso_sin_intentos_previos_no_guarda() {
        User user = user(true, 0);
        IssuedToken token = new IssuedToken("jwt", Instant.parse("2026-10-04T00:00:00Z"));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(tokenIssuer.issue(user, Duration.ofDays(7))).thenReturn(token);

        service.login(new LoginCommand("ana@x.co", "Abcdef12"));

        verify(userRepository, never()).save(any());
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
