package com.fusaroute.infrastructure;

import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import com.fusaroute.domain.port.out.UsageEventRecorderPort;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El camino completo de SCRUM-186 con la aplicacion entera y Postgres real: el appender
 * declarado en {@code logback-spring.xml} queda conectado al arrancar, un WARN llega a
 * {@code app_logs} sin secretos, un evento de uso llega a {@code search_events}, y los
 * endpoints de administrador los devuelven.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MonitoringEndToEndIT {

    private static final Logger LOG = LoggerFactory.getLogger("com.fusaroute.prueba.E2E");

    private static final String JWT =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3Iiwicm9sZSI6IkFETUlOIn0.c2lnbmF0dXJlRkFLRQ";

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
        registry.add("app.jwt.secret", () -> "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=");
        registry.add("app.totp.encryption-key", () -> "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=");
        registry.add("app.totp.issuer", () -> "FusaRoute");
        registry.add("google.maps.api.key", () -> "test-key");
        registry.add("app.logs.flush-interval-ms", () -> "50");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsageEventRecorderPort recorder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_full"), new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void al_arrancar_queda_la_fila_info_con_el_perfil() {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<String> filas = jdbc.queryForList(
                    "SELECT message FROM app_logs WHERE level = 'INFO' AND message LIKE 'FusaRoute iniciado%'",
                    String.class);
            assertThat(filas).isNotEmpty();
            assertThat(filas.getFirst()).contains("perfil=").contains("commit=").contains("version=");
        });
    }

    @Test
    void un_warn_llega_a_la_tabla_sin_secretos_y_un_info_no() {
        LOG.info("este INFO no debe guardarse E2E-INFO");
        LOG.warn("E2E-WARN Authorization: Bearer {} password=Secreta123", JWT);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<String> filas = jdbc.queryForList(
                    "SELECT message FROM app_logs WHERE message LIKE '%E2E-WARN%'", String.class);
            assertThat(filas).hasSize(1);
            assertThat(filas.getFirst()).doesNotContain("eyJ").doesNotContain("Secreta123");
        });
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM app_logs WHERE message LIKE '%E2E-INFO%'", Integer.class)).isZero();
    }

    @Test
    void un_error_con_excepcion_guarda_la_pila() {
        LOG.error("E2E-ERROR fallo inesperado", new IllegalStateException("causa E2E"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String pila = jdbc.queryForObject(
                    "SELECT exception FROM app_logs WHERE message LIKE '%E2E-ERROR%'", String.class);
            assertThat(pila).contains("IllegalStateException").contains("causa E2E");
        });
    }

    @Test
    void el_administrador_lee_los_logs_por_http_y_sin_token_no_entra() throws Exception {
        LOG.warn("E2E-HTTP un warn para leer por el endpoint");
        await().atMost(10, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM app_logs WHERE message LIKE '%E2E-HTTP%'", Integer.class) == 1);

        mockMvc.perform(get("/api/admin/logs").param("level", "WARN").param("limit", "50").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.message =~ /.*E2E-HTTP.*/)]").isNotEmpty());

        mockMvc.perform(get("/api/admin/logs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/logs").param("limit", "501").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void un_evento_de_uso_llega_a_la_tabla_y_se_lee_por_http() throws Exception {
        recorder.record(UsageEvent.of(Instant.now(), UsageEventType.SEARCH, "Centro E2E", "Terminal", 3L,
                SearchOutcome.FOUND, EtaSource.TRAFFIC, 420, false));

        await().atMost(10, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM search_events WHERE origin_label = 'Centro E2E'", Integer.class) == 1);

        mockMvc.perform(get("/api/admin/search-events").param("limit", "50").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.originLabel == 'Centro E2E')].outcome").value(contains("FOUND")));
    }
}
