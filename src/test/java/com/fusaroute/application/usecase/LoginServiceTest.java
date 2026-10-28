package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidCredentialsException;
import com.fusaroute.domain.exception.InvalidTotpException;
import com.fusaroute.domain.exception.TotpEnrollmentRequiredException;
import com.fusaroute.domain.exception.TotpRequiredException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.RecoveryCode;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.LoginCommand;
import com.fusaroute.domain.port.in.LoginResult;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.RecoveryCodePort;
import com.fusaroute.domain.port.out.RecoveryCodeRepositoryPort;
import com.fusaroute.domain.port.out.TokenIssuerPort;
import com.fusaroute.domain.port.out.TotpPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String HASH = "$2a$12$hash";
    private static final String TOTP_SECRET = "JBSWY3DPEHPK3PXP";
    private static final String CODE_HASH = "$2a$12$backupHash";

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private TokenIssuerPort tokenIssuer;

    @Mock
    private TotpPort totp;

    @Mock
    private RecoveryCodePort recoveryCodes;

    @Mock
    private RecoveryCodeRepositoryPort recoveryCodeRepository;

    @InjectMocks
    private LoginService service;

    /** Usuario sin TOTP activo (fuerza el bootstrap de enrolamiento). */
    private static User userWithoutTotp(boolean active, int failedAttempts) {
        return new User(7L, "Ana", Email.of("ana@x.co"), HASH, null, UserRole.USER, active,
                failedAttempts, false, null);
    }

    /** Usuario con TOTP activo: la ruta normal de login + segundo factor. */
    private static User userWithTotp(boolean active, int failedAttempts) {
        return new User(7L, "Ana", Email.of("ana@x.co"), HASH, null, UserRole.USER, active,
                failedAttempts, true, TOTP_SECRET);
    }

    // --- Validaciones basicas de credenciales (SCRUM-13 + SCRUM-36). -------------

    @Test
    void credenciales_correctas_con_totp_valido_emiten_token_de_siete_dias() {
        User user = userWithTotp(true, 0);
        IssuedToken token = new IssuedToken("jwt", Instant.parse("2026-10-04T00:00:00Z"));
        when(userRepository.findByEmail(new Email("ana@x.co"))).thenReturn(Optional.of(user));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(totp.verifyCode(TOTP_SECRET, "123456")).thenReturn(true);
        when(tokenIssuer.issue(user, Duration.ofDays(7))).thenReturn(token);

        LoginResult result = service.login(new LoginCommand("  Ana@X.CO ", "Abcdef12", "123456"));

        assertThat(result.user()).isSameAs(user);
        assertThat(result.token()).isSameAs(token);
    }

    @Test
    void contrasena_incorrecta_se_rechaza_y_no_emite_token() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithTotp(true, 0)));
        when(passwordHasher.matches("mala", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "mala")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenIssuer);
        verifyNoInteractions(totp);
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
        when(userRepository.findByEmail(new Email("ana@x.co"))).thenReturn(Optional.of(userWithTotp(true, 0)));
        when(passwordHasher.matches("mala", HASH)).thenReturn(false);
        when(passwordHasher.matches("mala", null)).thenReturn(false);

        String inexistente = messageOf(new LoginCommand("nadie@x.co", "mala"));
        String incorrecta = messageOf(new LoginCommand("ana@x.co", "mala"));

        assertThat(inexistente).isEqualTo(incorrecta);
    }

    @Test
    void usuario_inactivo_se_rechaza_aunque_la_contrasena_sea_correcta() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithTotp(false, 0)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "Abcdef12", "123456")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenIssuer);
        verifyNoInteractions(totp);
    }

    @Test
    void correo_con_formato_invalido_no_toca_los_puertos() {
        assertThatThrownBy(() -> service.login(new LoginCommand("correo-malo", "Abcdef12")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordHasher);
        verifyNoInteractions(tokenIssuer);
        verifyNoInteractions(totp);
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

    // --- Contador de intentos fallidos (SCRUM-36). -------------------------------

    @Test
    void contrasena_errada_de_usuario_existente_incrementa_el_contador() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithTotp(true, 2)));
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
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithTotp(false, 0)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "Abcdef12", "123456")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_exitoso_reinicia_el_contador_a_cero() {
        IssuedToken token = new IssuedToken("jwt", Instant.parse("2026-10-04T00:00:00Z"));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithTotp(true, 3)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(totp.verifyCode(TOTP_SECRET, "123456")).thenReturn(true);
        when(tokenIssuer.issue(any(), any())).thenReturn(token);

        LoginResult result = service.login(new LoginCommand("ana@x.co", "Abcdef12", "123456"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFailedLoginAttempts()).isZero();
        assertThat(result.user().getFailedLoginAttempts()).isZero();
    }

    @Test
    void login_exitoso_sin_intentos_previos_no_guarda() {
        User user = userWithTotp(true, 0);
        IssuedToken token = new IssuedToken("jwt", Instant.parse("2026-10-04T00:00:00Z"));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(totp.verifyCode(TOTP_SECRET, "123456")).thenReturn(true);
        when(tokenIssuer.issue(user, Duration.ofDays(7))).thenReturn(token);

        service.login(new LoginCommand("ana@x.co", "Abcdef12", "123456"));

        verify(userRepository, never()).save(any());
    }

    // --- Segundo factor TOTP (SCRUM-179). ----------------------------------------

    @Test
    void usuario_sin_totp_recibe_token_de_enrolamiento_y_no_token_full() {
        User user = userWithoutTotp(true, 0);
        IssuedToken enrollmentToken = new IssuedToken(
                "enroll.jwt", Instant.parse("2026-10-04T00:10:00Z"));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(tokenIssuer.issueEnrollmentToken(eq(user), eq(Duration.ofMinutes(10))))
                .thenReturn(enrollmentToken);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "Abcdef12")))
                .isInstanceOfSatisfying(TotpEnrollmentRequiredException.class,
                        e -> assertThat(e.getEnrollmentToken()).isSameAs(enrollmentToken));

        verify(tokenIssuer, never()).issue(any(), any());
        verifyNoInteractions(totp);
    }

    @Test
    void usuario_con_totp_sin_codigo_recibe_totp_required() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithTotp(true, 0)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "Abcdef12")))
                .isInstanceOf(TotpRequiredException.class);

        verifyNoInteractions(tokenIssuer);
        verifyNoInteractions(totp);
    }

    @Test
    void usuario_con_totp_con_codigo_vacio_recibe_totp_required() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithTotp(true, 0)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "Abcdef12", "   ")))
                .isInstanceOf(TotpRequiredException.class);
    }

    @Test
    void codigo_totp_incorrecto_cuenta_intento_fallido_y_lanza_invalid_totp() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithTotp(true, 1)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(totp.verifyCode(TOTP_SECRET, "000000")).thenReturn(false);
        when(recoveryCodeRepository.findActiveByUser(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "Abcdef12", "000000")))
                .isInstanceOf(InvalidTotpException.class);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFailedLoginAttempts()).isEqualTo(2);
        verify(tokenIssuer, never()).issue(any(), any());
    }

    @Test
    void codigo_de_respaldo_valido_consume_el_respaldo_y_emite_token_full() {
        User user = userWithTotp(true, 0);
        IssuedToken token = new IssuedToken("jwt", Instant.parse("2026-10-04T00:00:00Z"));
        RecoveryCode active = new RecoveryCode(42L, 7L, CODE_HASH, false);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(totp.verifyCode(TOTP_SECRET, "ABCD2345FG")).thenReturn(false);
        when(recoveryCodeRepository.findActiveByUser(7L)).thenReturn(List.of(active));
        when(recoveryCodes.matches("ABCD2345FG", CODE_HASH)).thenReturn(true);
        when(tokenIssuer.issue(user, Duration.ofDays(7))).thenReturn(token);

        LoginResult result = service.login(new LoginCommand("ana@x.co", "Abcdef12", "ABCD2345FG"));

        assertThat(result.token()).isSameAs(token);
        verify(recoveryCodeRepository).markUsed(active);
    }

    @Test
    void respaldo_no_coincidente_tampoco_pasa_y_cuenta_intento() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithTotp(true, 0)));
        when(passwordHasher.matches("Abcdef12", HASH)).thenReturn(true);
        when(totp.verifyCode(TOTP_SECRET, "AAAAAAAAAA")).thenReturn(false);
        when(recoveryCodeRepository.findActiveByUser(7L))
                .thenReturn(List.of(new RecoveryCode(42L, 7L, CODE_HASH, false)));
        when(recoveryCodes.matches("AAAAAAAAAA", CODE_HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand("ana@x.co", "Abcdef12", "AAAAAAAAAA")))
                .isInstanceOf(InvalidTotpException.class);

        verify(recoveryCodeRepository, never()).markUsed(any());
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
