package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidRegistrationException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.RegisterUserUseCase;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:4200")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterUserUseCase registerUserUseCase;

    @Test
    void registro_valido_responde_201_sin_hash_y_es_publico() throws Exception {
        User user = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                com.fusaroute.domain.model.UserRole.USER, true);
        when(registerUserUseCase.register(any())).thenReturn(user);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@x.co\",\"password\":\"Abcdef12\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("ana@x.co"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void datos_invalidos_responden_400_con_errors() throws Exception {
        when(registerUserUseCase.register(any())).thenThrow(new InvalidRegistrationException(
                List.of(new FieldViolation("password", "La contrasena es muy corta"))));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@x.co\",\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void correo_duplicado_responde_409() throws Exception {
        when(registerUserUseCase.register(any())).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@x.co\",\"password\":\"Abcdef12\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void json_malformado_responde_400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ roto"))
                .andExpect(status().isBadRequest());
    }
}
