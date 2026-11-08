package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidTotpException;
import com.fusaroute.domain.exception.TotpAlreadyEnabledException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.RecoveryCode;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.ConfirmTotpCommand;
import com.fusaroute.domain.port.in.ConfirmTotpResult;
import com.fusaroute.domain.port.out.RecoveryCodePort;
import com.fusaroute.domain.port.out.RecoveryCodeRepositoryPort;
import com.fusaroute.domain.port.out.TokenIssuerPort;
import com.fusaroute.domain.port.out.TotpPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmTotpServiceTest {

    private static final Instant CONFIRMADO_EN = Instant.parse("2026-11-07T10:00:00Z");

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private TotpPort totp;

    @Mock
    private RecoveryCodePort recoveryCodes;

    @Mock
    private RecoveryCodeRepositoryPort recoveryCodeRepository;

    @Mock
    private TokenIssuerPort tokenIssuer;

    /**
     * Instante fijo: confirmar el TOTP tambien registra el ingreso (SCRUM-101), y el
     * reloj real haria el assert de ese instante imposible de escribir.
     */
    private final Clock clock = Clock.fixed(CONFIRMADO_EN, ZoneOffset.UTC);

    private ConfirmTotpService service;

    @BeforeEach
    void setUp() {
        service = new ConfirmTotpService(userRepository, totp, recoveryCodes,
                recoveryCodeRepository, tokenIssuer, clock);
    }

    /**
     * Confirmar deja al usuario con sesion abierta, asi que cuenta como ingreso y debe
     * quedar registrado (SCRUM-101). Si no se registrara, las respuestas que reciba
     * durante esa primera sesion de 7 dias no apareceran en el contador de RF-14
     * cuando vuelva a entrar, porque no habria un ingreso anterior con el que
     * compararlas. Es un defecto silencioso, y por eso tiene test propio.
     */
    @org.junit.jupiter.api.Test
    void confirmar_registra_el_ingreso_porque_deja_sesion_abierta() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userEnrolando()));
        when(totp.verifyCode("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recoveryCodes.generatePlainCodes(10)).thenReturn(List.of(
                "AAAA1111BB", "CCCC2222DD", "EEEE3333FF", "GGGG4444HH", "IIII5555JJ",
                "KKKK6666LL", "MMMM7777NN", "PPPP8888QQ", "RRRR9999SS", "TTTTAAAAUU"));
        when(recoveryCodes.hash(any())).thenAnswer(inv -> "H:" + inv.getArgument(0));
        when(tokenIssuer.issue(any(), eq(Duration.ofDays(7))))
                .thenReturn(new IssuedToken("full.jwt", CONFIRMADO_EN.plusSeconds(60)));

        service.confirm(new ConfirmTotpCommand(7L, "123456"));

        ArgumentCaptor<User> guardado = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(guardado.capture());
        assertThat(guardado.getValue().getLastLoginAt()).isEqualTo(CONFIRMADO_EN);
        // Primer ingreso: no hay anterior con el que comparar.
        assertThat(guardado.getValue().getPreviousLoginAt()).isNull();
    }

    private static User userEnrolando() {
        return new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, false, "JBSWY3DPEHPK3PXP", null);
    }

    @Test
    void confirmar_con_codigo_valido_activa_y_devuelve_token_full_y_respaldos() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userEnrolando()));
        when(totp.verifyCode("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        List<String> plain = List.of(
                "AAAA1111BB", "CCCC2222DD", "EEEE3333FF", "GGGG4444HH", "IIII5555JJ",
                "KKKK6666LL", "MMMM7777NN", "PPPP8888QQ", "RRRR9999SS", "TTTTAAAAUU");
        when(recoveryCodes.generatePlainCodes(10)).thenReturn(plain);
        when(recoveryCodes.hash(any())).thenAnswer(inv -> "H:" + inv.getArgument(0));
        IssuedToken token = new IssuedToken("full.jwt", Instant.parse("2026-10-30T00:00:00Z"));
        when(tokenIssuer.issue(any(), eq(Duration.ofDays(7)))).thenReturn(token);

        ConfirmTotpResult result = service.confirm(new ConfirmTotpCommand(7L, "123456"));

        assertThat(result.user().isTotpEnabled()).isTrue();
        assertThat(result.token()).isSameAs(token);
        assertThat(result.recoveryCodes()).isEqualTo(plain);

        // Reemplazo COMPLETO: cualquier codigo previo del usuario queda invalidado.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecoveryCode>> captured = ArgumentCaptor.forClass(List.class);
        verify(recoveryCodeRepository).replaceAllForUser(eq(7L), captured.capture());
        assertThat(captured.getValue()).hasSize(10);
        assertThat(captured.getValue()).allSatisfy(rc -> {
            assertThat(rc.getCodeHash()).startsWith("H:");
            assertThat(rc.isUsed()).isFalse();
        });
    }

    @Test
    void confirmar_con_codigo_invalido_lanza_y_no_activa() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userEnrolando()));
        when(totp.verifyCode("JBSWY3DPEHPK3PXP", "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(new ConfirmTotpCommand(7L, "000000")))
                .isInstanceOf(InvalidTotpException.class);

        verify(userRepository, never()).save(any());
        verify(recoveryCodeRepository, never()).replaceAllForUser(any(), any());
        verify(tokenIssuer, never()).issue(any(), any());
    }

    @Test
    void confirmar_sin_enrolamiento_previo_lanza_invalid_totp() {
        User sinSecreto = new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, false, null, null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(sinSecreto));

        assertThatThrownBy(() -> service.confirm(new ConfirmTotpCommand(7L, "123456")))
                .isInstanceOf(InvalidTotpException.class);
    }

    @Test
    void confirmar_con_totp_ya_activo_lanza() {
        User yaActivo = new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, true, "JBSWY3DPEHPK3PXP", null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(yaActivo));

        assertThatThrownBy(() -> service.confirm(new ConfirmTotpCommand(7L, "123456")))
                .isInstanceOf(TotpAlreadyEnabledException.class);
    }

    @Test
    void usuario_inexistente_lanza() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(new ConfirmTotpCommand(99L, "123456")))
                .isInstanceOf(UserNotFoundException.class);
    }
}
