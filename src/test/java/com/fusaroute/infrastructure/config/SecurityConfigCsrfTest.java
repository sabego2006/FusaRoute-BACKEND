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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF scope (T03, Sonar S4502). The exemption covers only /api/**, where the JWT
 * travels in the Authorization header and a cross-site request cannot carry it.
 * Everywhere else Spring Security's CSRF protection stays on. If someone goes back
 * to csrf.disable(), the POST outside /api stops being rejected and the first test
 * fails; if someone drops the exemption, the login POST starts failing with 403.
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
    void un_post_fuera_del_api_sin_token_csrf_es_rechazado_con_403() throws Exception {
        mockMvc.perform(post("/health"))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_post_al_api_no_exige_token_csrf_porque_el_jwt_va_en_el_header() throws Exception {
        User user = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null, UserRole.USER, true, 0);
        when(loginUseCase.login(any())).thenReturn(new LoginResult(user,
                new IssuedToken("el.jwt.firmado", Instant.parse("2026-10-05T12:00:00Z"))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@x.co\",\"password\":\"Abcdef12\"}"))
                .andExpect(status().isOk());
    }
}
