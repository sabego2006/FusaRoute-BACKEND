package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.IncorrectCurrentPasswordException;
import com.fusaroute.domain.exception.InvalidProfileException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.ChangePasswordCommand;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
class ChangePasswordServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @InjectMocks
    private ChangePasswordService service;

    private final User existing = new User(1L, "Ana", Email.of("ana@x.co"),
            "$2a$12$hash", null, UserRole.USER, true);

    @Test
    void cambio_valido_hashea_y_guarda() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordHasher.matches("ViejaPass1", "$2a$12$hash")).thenReturn(true);
        when(passwordHasher.hash("NuevaPass1")).thenReturn("$2a$12$nuevo");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(new ChangePasswordCommand(1L, "ViejaPass1", "NuevaPass1"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$12$nuevo");
    }

    @Test
    void contrasena_actual_incorrecta_no_guarda() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordHasher.matches("Mala1234", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                new ChangePasswordCommand(1L, "Mala1234", "NuevaPass1")))
                .isInstanceOf(IncorrectCurrentPasswordException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void nueva_contrasena_invalida_no_guarda() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordHasher.matches("ViejaPass1", "$2a$12$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(
                new ChangePasswordCommand(1L, "ViejaPass1", "abc")))
                .isInstanceOf(InvalidProfileException.class);

        verify(userRepository, never()).save(any());
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    void usuario_inexistente_lanza_excepcion() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(
                new ChangePasswordCommand(99L, "Vieja1234", "Nueva1234")))
                .isInstanceOf(UserNotFoundException.class);
    }
}
