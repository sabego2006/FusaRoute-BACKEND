package com.fusaroute.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo del segundo factor con la aplicacion entera levantada y el
 * adaptador TOTP REAL — no se mockea {@code TotpPort}.
 *
 * Existe por una razon concreta: hasta SCRUM-179 todos los tests del segundo
 * factor mockeaban el puerto, asi que el adaptador podia quedar lanzando
 * {@code UnsupportedOperationException} con la suite entera en verde y el login
 * roto en ejecucion. Este IT es la red que faltaba: si el adaptador vuelve a
 * quedar sin implementar, falla aqui.
 *
 * Los codigos no se escriben a mano: se calculan con la misma libreria, igual
 * que lo haria la app autenticadora del usuario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TotpLoginFlowIT {

    private static final String PASSWORD = "Password1";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:4200");
        // Claves solo de prueba: 32 bytes en Base64.
        registry.add("app.jwt.secret", () -> "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=");
        registry.add("app.totp.encryption-key", () -> "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=");
        registry.add("app.totp.issuer", () -> "FusaRoute");
        registry.add("google.maps.api.key", () -> "test-key");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void un_usuario_nuevo_queda_obligado_a_enrolar_y_despues_entra_con_su_codigo() throws Exception {
        String email = "flujo-totp@x.co";
        register(email);

        // 1. Contrasena correcta pero sin segundo factor: nadie entra. El backend
        //    entrega el token de enrolamiento para arrancar el flujo forzado.
        String enrollmentToken = loginExpectingEnrollment(email);
        assertThat(enrollmentToken).isNotBlank();

        // 2. El token de enrolamiento SOLO abre /enroll y /confirm.
        mockMvc.perform(post("/api/users/me/totp/recovery-codes")
                        .header("Authorization", "Bearer " + enrollmentToken))
                .andExpect(status().isForbidden());

        // 3. Enrolar: aqui es donde el adaptador en esqueleto reventaba en 500.
        JsonNode enrollment = json(mockMvc.perform(post("/api/users/me/totp/enroll")
                        .header("Authorization", "Bearer " + enrollmentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String secret = enrollment.get("secret").asText();
        assertThat(enrollment.get("otpauthUri").asText()).startsWith("otpauth://totp/");
        assertThat(enrollment.get("qrPngBase64").asText()).isNotBlank();

        // 4. Confirmar con el codigo que calcula la propia libreria = quedar logueado.
        JsonNode confirmed = json(mockMvc.perform(post("/api/users/me/totp/confirm")
                        .header("Authorization", "Bearer " + enrollmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + currentCode(secret) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(confirmed.get("token").asText()).isNotBlank();
        assertThat(confirmed.get("recoveryCodes")).hasSize(10);
        String fullToken = confirmed.get("token").asText();
        String recoveryCode = confirmed.get("recoveryCodes").get(0).asText();

        // 5. Con el factor activo, el login sin codigo pide el segundo paso.
        mockMvc.perform(login(email, PASSWORD, null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOTP_REQUIRED"));

        // 6. Codigo errado: rechazo con pista, sin revelar nada mas.
        mockMvc.perform(login(email, PASSWORD, "000000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOTP"));

        // 7. Codigo correcto: entra.
        mockMvc.perform(login(email, PASSWORD, currentCode(secret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // 8. Codigo de respaldo: entra una vez y solo una.
        mockMvc.perform(login(email, PASSWORD, recoveryCode))
                .andExpect(status().isOk());
        mockMvc.perform(login(email, PASSWORD, recoveryCode))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOTP"));

        // 9. Regenerar respaldos: el conjunto viejo deja de servir.
        JsonNode regenerated = json(mockMvc.perform(post("/api/users/me/totp/recovery-codes")
                        .header("Authorization", "Bearer " + fullToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(regenerated.get("recoveryCodes")).hasSize(10);

        String codigoViejoNoUsado = confirmed.get("recoveryCodes").get(1).asText();
        mockMvc.perform(login(email, PASSWORD, codigoViejoNoUsado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOTP"));
        mockMvc.perform(login(email, PASSWORD, regenerated.get("recoveryCodes").get(0).asText()))
                .andExpect(status().isOk());
    }

    @Test
    void re_enrolar_desde_el_perfil_apaga_el_factor_anterior_y_activa_el_nuevo() throws Exception {
        String email = "cambio-de-telefono@x.co";
        register(email);
        String enrollmentToken = loginExpectingEnrollment(email);

        String primerSecreto = json(mockMvc.perform(post("/api/users/me/totp/enroll")
                        .header("Authorization", "Bearer " + enrollmentToken))
                .andReturn().getResponse().getContentAsString()).get("secret").asText();
        String fullToken = json(mockMvc.perform(post("/api/users/me/totp/confirm")
                        .header("Authorization", "Bearer " + enrollmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + currentCode(primerSecreto) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        // El token de enrolamiento, aun vigente, NO puede borrar el factor confirmado.
        mockMvc.perform(post("/api/users/me/totp/enroll")
                        .header("Authorization", "Bearer " + enrollmentToken))
                .andExpect(status().isConflict());

        // El token completo si: es el boton "volver a configurar" del perfil.
        String segundoSecreto = json(mockMvc.perform(post("/api/users/me/totp/enroll")
                        .header("Authorization", "Bearer " + fullToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("secret").asText();
        assertThat(segundoSecreto).isNotEqualTo(primerSecreto);

        mockMvc.perform(post("/api/users/me/totp/confirm")
                        .header("Authorization", "Bearer " + fullToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + currentCode(segundoSecreto) + "\"}"))
                .andExpect(status().isOk());

        // El telefono viejo ya no abre la cuenta; el nuevo si.
        mockMvc.perform(login(email, PASSWORD, currentCode(primerSecreto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOTP"));
        mockMvc.perform(login(email, PASSWORD, currentCode(segundoSecreto)))
                .andExpect(status().isOk());
    }

    // --- helpers ---

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    private String loginExpectingEnrollment(String email) throws Exception {
        String body = mockMvc.perform(login(email, PASSWORD, null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOTP_ENROLLMENT_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        return json(body).get("enrollmentToken").asText();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password, String totpCode) {
        String code = totpCode == null ? "" : ",\"totpCode\":\"" + totpCode + "\"";
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"" + code + "}");
    }

    /** El codigo que la app autenticadora mostraria ahora mismo para ese secreto. */
    private static String currentCode(String secret) throws Exception {
        long bucket = new SystemTimeProvider().getTime() / 30;
        return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret, bucket);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
