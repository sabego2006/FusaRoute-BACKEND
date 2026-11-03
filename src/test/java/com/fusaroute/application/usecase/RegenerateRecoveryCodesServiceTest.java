package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.TotpNotEnabledException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.RecoveryCode;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.RegenerateRecoveryCodesResult;
import com.fusaroute.domain.port.out.RecoveryCodePort;
import com.fusaroute.domain.port.out.RecoveryCodeRepositoryPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegenerateRecoveryCodesServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private RecoveryCodePort recoveryCodes;

    @Mock
    private RecoveryCodeRepositoryPort recoveryCodeRepository;

    private RegenerateRecoveryCodesService service() {
        return new RegenerateRecoveryCodesService(userRepository, recoveryCodes, recoveryCodeRepository);
    }

    private static User userConTotpActivo() {
        return new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, true, "SECRETO", null);
    }

    @Test
    void regenera_diez_codigos_hasheados_y_reemplaza_los_anteriores() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userConTotpActivo()));
        List<String> plain = List.of("AAAA1111BB", "CCCC2222DD", "EEEE3333FF", "GGGG4444HH", "JJJJ5555KK",
                "MMMM6666NN", "PPPP7777QQ", "RRRR8888SS", "TTTT9999UU", "VVVV2222WW");
        when(recoveryCodes.generatePlainCodes(10)).thenReturn(plain);
        when(recoveryCodes.hash(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));

        RegenerateRecoveryCodesResult result = service().regenerate(7L);

        // El usuario recibe los codigos en claro una sola vez...
        assertThat(result.codes()).isEqualTo(plain);

        // ...y lo que se persiste son los hashes, nunca el claro.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecoveryCode>> saved = ArgumentCaptor.forClass(List.class);
        verify(recoveryCodeRepository).replaceAllForUser(org.mockito.ArgumentMatchers.eq(7L), saved.capture());
        assertThat(saved.getValue()).hasSize(10);
        assertThat(saved.getValue()).allSatisfy(code -> {
            assertThat(code.getUserId()).isEqualTo(7L);
            assertThat(code.getCodeHash()).startsWith("hash:");
            assertThat(code.isUsed()).isFalse();
        });
        assertThat(saved.getValue()).map(RecoveryCode::getCodeHash)
                .containsExactlyElementsOf(plain.stream().map(c -> "hash:" + c).toList());
    }

    @Test
    void usuario_sin_totp_activo_no_recibe_codigos() {
        User sinTotp = new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, false, null, null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(sinTotp));

        assertThatThrownBy(() -> service().regenerate(7L))
                .isInstanceOf(TotpNotEnabledException.class);

        // Nada se emite ni se persiste: emitir respaldos sin factor seria una
        // via de entrada que no exige el segundo factor.
        verify(recoveryCodes, never()).generatePlainCodes(org.mockito.ArgumentMatchers.anyInt());
        verify(recoveryCodeRepository, never()).replaceAllForUser(anyLong(), any());
    }

    @Test
    void usuario_inexistente_lanza() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().regenerate(99L))
                .isInstanceOf(UserNotFoundException.class);

        verify(recoveryCodeRepository, never()).replaceAllForUser(anyLong(), any());
    }
}
