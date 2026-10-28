package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.InvalidRegistrationException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.RegisterUserCommand;
import com.fusaroute.domain.port.out.PasswordHasherPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @InjectMocks
    private RegisterUserService service;

    @Test
    void registro_valido_guarda_hash_correo_normalizado_rol_user_y_activo() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordHasher.hash("Abcdef12")).thenReturn("$2a$12$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(new RegisterUserCommand("  Ana ", "  Ana@X.CO ", "Abcdef12"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Ana");
        assertThat(saved.getEmail().value()).isEqualTo("ana@x.co");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(saved.getPasswordHash()).isNotEqualTo("Abcdef12");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void correo_duplicado_no_hashea_ni_guarda() {
        when(userRepository.existsByEmail(new Email("ana@x.co"))).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("Ana", "ana@x.co", "Abcdef12")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verifyNoInteractions(passwordHasher);
        verify(userRepository, never()).save(any());
    }

    @Test
    void contrasena_invalida_no_toca_los_puertos() {
        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("Ana", "ana@x.co", "abc")))
                .isInstanceOf(InvalidRegistrationException.class);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void varios_errores_se_devuelven_juntos() {
        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("", "correo-malo", "abc")))
                .isInstanceOfSatisfying(InvalidRegistrationException.class, e ->
                        assertThat(e.getViolations())
                                .extracting("field")
                                .contains("name", "email", "password"));

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordHasher);
    }
}
