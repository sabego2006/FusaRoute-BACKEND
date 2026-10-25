package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTotpStatusServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private GetTotpStatusService service;

    @Test
    void devuelve_true_cuando_el_usuario_tiene_totp_activo() {
        User user = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, true, "SECRETO");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(service.isEnabled(1L)).isTrue();
    }

    @Test
    void devuelve_false_cuando_el_usuario_no_lo_tiene() {
        User user = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, false, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(service.isEnabled(1L)).isFalse();
    }

    @Test
    void usuario_inexistente_lanza() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.isEnabled(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
