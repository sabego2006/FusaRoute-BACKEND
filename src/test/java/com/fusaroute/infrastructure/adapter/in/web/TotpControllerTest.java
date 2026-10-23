package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.InvalidTotpException;
import com.fusaroute.domain.exception.TotpAlreadyEnabledException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.ConfirmTotpResult;
import com.fusaroute.domain.port.in.ConfirmTotpUseCase;
import com.fusaroute.domain.port.in.EnrollTotpUseCase;
import com.fusaroute.domain.port.in.GetTotpStatusUseCase;
import com.fusaroute.domain.port.in.TotpEnrollmentResult;
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

@WebMvcTest(TotpController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class TotpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetTotpStatusUseCase getStatus;

    @MockitoBean
    private EnrollTotpUseCase enroll;

    @MockitoBean
    private ConfirmTotpUseCase confirm;

    @Test
    void sin_token_responde_401() throws Exception {
        mockMvc.perform(get("/api/users/me/totp"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void status_con_token_full_devuelve_estado() throws Exception {
        when(getStatus.isEnabled(1L)).thenReturn(true);

        mockMvc.perform(get("/api/users/me/totp")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void status_con_token_de_enrolamiento_responde_403() throws Exception {
        // El token de enrolamiento SOLO abre /enroll y /confirm. Status exige scope=full.
        mockMvc.perform(get("/api/users/me/totp")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "totp_enroll"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void enroll_funciona_con_token_full_o_con_token_de_enrolamiento() throws Exception {
        when(enroll.enroll(1L)).thenReturn(new TotpEnrollmentResult(
                "SECRETO", "otpauth://totp/FusaRoute:ana@x.co?secret=SECRETO&issuer=FusaRoute",
                "PNGBASE64"));

        mockMvc.perform(post("/api/users/me/totp/enroll")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "totp_enroll"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("SECRETO"))
                .andExpect(jsonPath("$.otpauthUri").value(
                        "otpauth://totp/FusaRoute:ana@x.co?secret=SECRETO&issuer=FusaRoute"))
                .andExpect(jsonPath("$.qrPngBase64").value("PNGBASE64"));

        mockMvc.perform(post("/api/users/me/totp/enroll")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk());
    }

    @Test
    void confirm_valido_devuelve_token_full_y_respaldos() throws Exception {
        User activated = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, true, "SECRETO");
        List<String> plain = List.of("AAAA1111BB", "CCCC2222DD");
        when(confirm.confirm(any())).thenReturn(new ConfirmTotpResult(
                activated,
                new IssuedToken("full.jwt", Instant.parse("2026-10-30T00:00:00Z")),
                plain));

        mockMvc.perform(post("/api/users/me/totp/confirm")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "totp_enroll")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("full.jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.recoveryCodes[0]").value("AAAA1111BB"))
                .andExpect(jsonPath("$.recoveryCodes.length()").value(2))
                .andExpect(jsonPath("$.user.email").value("ana@x.co"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void confirm_con_codigo_invalido_responde_401() throws Exception {
        when(confirm.confirm(any())).thenThrow(new InvalidTotpException());

        mockMvc.perform(post("/api/users/me/totp/confirm")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "totp_enroll")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOTP"));
    }

    @Test
    void enroll_con_totp_ya_activo_responde_409() throws Exception {
        when(enroll.enroll(1L)).thenThrow(new TotpAlreadyEnabledException());

        mockMvc.perform(post("/api/users/me/totp/enroll")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isConflict());
    }
}
