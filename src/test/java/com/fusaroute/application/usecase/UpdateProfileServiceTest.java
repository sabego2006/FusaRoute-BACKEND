package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.InvalidProfileException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.UpdateProfileCommand;
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
class UpdateProfileServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private UpdateProfileService service;

    private final User existing = new User(1L, "Ana", Email.of("ana@x.co"),
            "$2a$12$hash", null, UserRole.USER, true);

    @Test
    void actualizacion_valida_guarda_nombre_correo_y_telefono() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateProfile(new UpdateProfileCommand(1L, "Ana M.", "ana@x.co", "3101234567"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Ana M.");
        assertThat(saved.getPhone()).isEqualTo("3101234567");
    }

    @Test
    void correo_nuevo_verifica_duplicados() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail(Email.of("otra@x.co"))).thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile(
                new UpdateProfileCommand(1L, "Ana", "otra@x.co", null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void mismo_correo_no_verifica_duplicados() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateProfile(new UpdateProfileCommand(1L, "Ana", "ana@x.co", null));

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void nombre_vacio_lanza_invalid_profile() {
        assertThatThrownBy(() -> service.updateProfile(
                new UpdateProfileCommand(1L, "", "ana@x.co", null)))
                .isInstanceOfSatisfying(InvalidProfileException.class, e ->
                        assertThat(e.getViolations())
                                .extracting("field")
                                .contains("name"));
    }

    @Test
    void telefono_invalido_lanza_invalid_profile() {
        assertThatThrownBy(() -> service.updateProfile(
                new UpdateProfileCommand(1L, "Ana", "ana@x.co", "abc")))
                .isInstanceOfSatisfying(InvalidProfileException.class, e ->
                        assertThat(e.getViolations())
                                .extracting("field")
                                .contains("phone"));
    }

    @Test
    void usuario_inexistente_lanza_excepcion() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(
                new UpdateProfileCommand(99L, "Ana", "ana@x.co", null)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
