package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.IncorrectCurrentPasswordException;
import com.fusaroute.domain.exception.InvalidProfileException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.ChangePasswordUseCase;
import com.fusaroute.domain.port.in.GetProfileUseCase;
import com.fusaroute.domain.port.in.UpdateProfileUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetProfileUseCase getProfile;

    @MockitoBean
    private UpdateProfileUseCase updateProfile;

    @MockitoBean
    private ChangePasswordUseCase changePassword;

    private final User user = new User(1L, "Ana", Email.of("ana@x.co"),
            "$2a$12$hash", "3101234567", UserRole.USER, true);

    // --- GET /api/users/me ---

    @Test
    void sin_token_responde_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_perfil_devuelve_200_sin_hash() throws Exception {
        when(getProfile.getProfile(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@x.co"))
                .andExpect(jsonPath("$.phone").value("3101234567"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // --- PUT /api/users/me ---

    @Test
    void update_valido_responde_200() throws Exception {
        User updated = new User(1L, "Ana Maria", Email.of("nueva@x.co"),
                "$2a$12$hash", "3109876543", UserRole.USER, true);
        when(updateProfile.updateProfile(any())).thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Maria","email":"nueva@x.co","phone":"3109876543"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Maria"))
                .andExpect(jsonPath("$.email").value("nueva@x.co"))
                .andExpect(jsonPath("$.phone").value("3109876543"));
    }

    @Test
    void update_datos_invalidos_responde_400_con_errors() throws Exception {
        when(updateProfile.updateProfile(any())).thenThrow(
                new InvalidProfileException(List.of(new FieldViolation("name", "El nombre es obligatorio"))));

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"ana@x.co","phone":null}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void update_correo_duplicado_responde_409() throws Exception {
        when(updateProfile.updateProfile(any())).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ocupado@x.co","phone":null}"""))
                .andExpect(status().isConflict());
    }

    // --- PUT /api/users/me/password ---

    @Test
    void cambio_de_contrasena_valido_responde_204() throws Exception {
        doNothing().when(changePassword).changePassword(any());

        mockMvc.perform(put("/api/users/me/password")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"ViejaPass1","newPassword":"NuevaPass1"}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    void contrasena_actual_incorrecta_responde_400() throws Exception {
        doThrow(new IncorrectCurrentPasswordException()).when(changePassword).changePassword(any());

        mockMvc.perform(put("/api/users/me/password")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Mala1234","newPassword":"NuevaPass1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contrasena actual no es correcta"));
    }

    @Test
    void nueva_contrasena_invalida_responde_400_con_errors() throws Exception {
        doThrow(new InvalidProfileException(
                List.of(new FieldViolation("password", "La contrasena debe tener al menos 8 caracteres"))))
                .when(changePassword).changePassword(any());

        mockMvc.perform(put("/api/users/me/password")
                        .with(jwt().jwt(j -> j.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"ViejaPass1","newPassword":"abc"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }
}
