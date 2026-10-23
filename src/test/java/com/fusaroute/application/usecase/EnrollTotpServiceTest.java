package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.TotpAlreadyEnabledException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.TotpEnrollmentResult;
import com.fusaroute.domain.port.out.TotpPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollTotpServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private TotpPort totp;

    private EnrollTotpService service() {
        return new EnrollTotpService(userRepository, totp, "FusaRoute");
    }

    private static User userSinTotp() {
        return new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, false, null);
    }

    @Test
    void enrolar_genera_secreto_y_guarda_pendiente_de_confirmar() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userSinTotp()));
        when(totp.generateSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totp.provisioningUri("JBSWY3DPEHPK3PXP", "ana@x.co", "FusaRoute"))
                .thenReturn("otpauth://totp/FusaRoute:ana@x.co?secret=JBSWY3DPEHPK3PXP&issuer=FusaRoute");
        when(totp.qrPngBase64(any())).thenReturn("PNGBASE64");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TotpEnrollmentResult result = service().enroll(7L);

        assertThat(result.secret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(result.otpauthUri()).contains("secret=JBSWY3DPEHPK3PXP");
        assertThat(result.qrPngBase64()).isEqualTo("PNGBASE64");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        // El secreto se guarda pero el segundo factor NO queda activo todavia.
        assertThat(saved.getValue().getTotpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(saved.getValue().isTotpEnabled()).isFalse();
    }

    @Test
    void re_enrolar_antes_de_confirmar_reemplaza_el_secreto_pendiente() {
        User conSecretoPendiente = new User(7L, "Ana", Email.of("ana@x.co"),
                "$2a$12$hash", null, UserRole.USER, true, 0, false, "ANTERIOR");
        when(userRepository.findById(7L)).thenReturn(Optional.of(conSecretoPendiente));
        when(totp.generateSecret()).thenReturn("NUEVO");
        when(totp.provisioningUri(any(), any(), any())).thenReturn("otpauth://...");
        when(totp.qrPngBase64(any())).thenReturn("PNG");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().enroll(7L);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getTotpSecret()).isEqualTo("NUEVO");
    }

    @Test
    void enrolar_con_totp_ya_activo_lanza() {
        User yaActivo = new User(7L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, true, "SECRETO");
        when(userRepository.findById(7L)).thenReturn(Optional.of(yaActivo));

        assertThatThrownBy(() -> service().enroll(7L))
                .isInstanceOf(TotpAlreadyEnabledException.class);

        verify(userRepository, never()).save(any());
        verify(totp, never()).generateSecret();
    }

    @Test
    void usuario_inexistente_lanza() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().enroll(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
