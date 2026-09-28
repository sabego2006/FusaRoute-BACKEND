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
class GetProfileServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private GetProfileService service;

    @Test
    void perfil_existente_devuelve_usuario() {
        User user = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", "3101234567",
                UserRole.USER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = service.getProfile(1L);

        assertThat(result.getName()).isEqualTo("Ana");
        assertThat(result.getEmail().value()).isEqualTo("ana@x.co");
        assertThat(result.getPhone()).isEqualTo("3101234567");
    }

    @Test
    void usuario_inexistente_lanza_excepcion() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
