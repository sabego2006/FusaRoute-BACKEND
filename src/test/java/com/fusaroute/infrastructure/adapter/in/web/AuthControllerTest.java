package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidCredentialsException;
import com.fusaroute.domain.exception.InvalidRegistrationException;
import com.fusaroute.domain.exception.InvalidTotpException;
import com.fusaroute.domain.exception.TotpEnrollmentRequiredException;
import com.fusaroute.domain.exception.TotpRequiredException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.LoginResult;
import com.fusaroute.domain.port.in.LoginUseCase;
import com.fusaroute.domain.port.in.RegisterUserUseCase;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        // 32 bytes en Base64, el minimo que exige JwtConfig.
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterUserUseCase registerUserUseCase;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @Test
    void login_valido_responde_200_con_token_y_sin_hash_y_es_publico() throws Exception {
        User user = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                com.fusaroute.domain.model.UserRole.USER, true, 0);
        when(loginUseCase.login(any())).thenReturn(new LoginResult(user,
                new IssuedToken("el.jwt.firmado", Instant.parse("2026-10-05T12:00:00Z"))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@x.co\",\"password\":\"Abcdef12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("el.jwt.firmado"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-05T12:00:00Z"))
                .andExpect(jsonPath("$.user.email").value("ana@x.co"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void credenciales_invalidas_responden_401_con_mensaje_generico() throws Exception {
        when(loginUseCase.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@x.co\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contrasena incorrectos"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void sin_totp_enrolado_responde_401_con_error_y_enrollment_token() throws Exception {
        when(loginUseCase.login(any())).thenThrow(
                new TotpEnrollmentRequiredException(
                        new IssuedToken("enroll.jwt", Instant.parse("2026-10-05T12:10:00Z"))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@x.co\",\"password\":\"Abcdef12\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOTP_ENROLLMENT_REQUIRED"))
                .andExpect(jsonPath("$.enrollmentToken").value("enroll.jwt"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-05T12:10:00Z"));
    }

    @Test
    void totp_requerido_responde_401_con_error_totp_required() throws Exception {
        when(loginUseCase.login(any())).thenThrow(new TotpRequiredException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@x.co\",\"password\":\"Abcdef12\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOTP_REQUIRED"));
    }

    @Test
    void totp_invalido_responde_401_con_error_invalid_totp() throws Exception {
        when(loginUseCase.login(any())).thenThrow(new InvalidTotpException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@x.co\",\"password\":\"Abcdef12\",\"totpCode\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOTP"));
    }

    @Test
    void un_endpoint_protegido_sin_token_responde_401_con_cuerpo_json() throws Exception {
        mockMvc.perform(get("/api/cualquier-cosa-protegida"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Autenticacion requerida"));
    }

    @Test
    void autenticado_una_ruta_inexistente_responde_404_y_no_500() throws Exception {
        // Con SCRUM-179 el "autenticado" normal exige scope=full: un token sin el
        // claim NO sirve para alcanzar endpoints protegidos.
        mockMvc.perform(get("/api/no-existe").with(jwt().jwt(j -> j.claim("scope", "full"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void autenticado_un_metodo_no_permitido_responde_405_y_no_500() throws Exception {
        mockMvc.perform(get("/api/auth/login").with(jwt().jwt(j -> j.claim("scope", "full"))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void un_endpoint_protegido_con_token_basura_responde_401() throws Exception {
        mockMvc.perform(get("/api/cualquier-cosa-protegida")
                        .header("Authorization", "Bearer no.es.un.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registro_valido_responde_201_sin_hash_y_es_publico() throws Exception {
        User user = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                com.fusaroute.domain.model.UserRole.USER, true, 0);
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
