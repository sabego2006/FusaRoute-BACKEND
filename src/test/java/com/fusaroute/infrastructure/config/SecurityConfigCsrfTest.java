package com.fusaroute.infrastructure.config;

import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.LoginResult;
import com.fusaroute.domain.port.in.LoginUseCase;
import com.fusaroute.domain.port.in.RegisterUserUseCase;
import com.fusaroute.infrastructure.adapter.in.web.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Why CSRF does not apply here (T03, Sonar S4502). CSRF only works when the browser
 * attaches the credentials by itself, i.e. a session cookie. This API never issues
 * one: the JWT travels in the Authorization header, which a cross-site form or
 * fetch cannot set. These tests pin that premise down. If someone introduces a
 * session cookie (JSESSIONID in Set-Cookie), the second test fails and the CSRF
 * decision has to be revisited; if someone turns CSRF back on without a token
 * repository the frontend can use, the login POST starts failing with 403.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class SecurityConfigCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterUserUseCase registerUserUseCase;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @Test
    void un_post_al_api_no_exige_token_csrf_y_el_login_no_entrega_cookie_de_sesion() throws Exception {
        User user = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null, UserRole.USER, true, 0);
        when(loginUseCase.login(any())).thenReturn(new LoginResult(user,
                new IssuedToken("el.jwt.firmado", Instant.parse("2026-10-05T12:00:00Z"))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@x.co\",\"password\":\"Abcdef12\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(SET_COOKIE));
    }

    @Test
    void una_peticion_autenticada_con_jwt_no_abre_sesion_ni_entrega_cookie() throws Exception {
        mockMvc.perform(get("/api/auth/no-existe").with(jwt()))
                .andExpect(header().doesNotExist(SET_COOKIE));
    }
}
