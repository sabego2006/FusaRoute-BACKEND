package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.InvalidMonitoringQueryException;
import com.fusaroute.domain.model.AppLog;
import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.LogLevel;
import com.fusaroute.domain.model.SearchOutcome;
import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.model.UsageEventType;
import com.fusaroute.domain.port.in.ListAppLogsUseCase;
import com.fusaroute.domain.port.in.ListUsageEventsUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de los endpoints de monitoreo del administrador (SCRUM-186). Con el
 * {@code SecurityConfig} real: lo que se verifica es que sin rol de administrador no se
 * lee ni una fila, no que el controlador compile.
 */
@WebMvcTest(AdminMonitoringController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class AdminMonitoringControllerTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListAppLogsUseCase listAppLogs;

    @MockitoBean
    private ListUsageEventsUseCase listUsageEvents;

    /** Mismas authorities que produce el converter de produccion (ver AdminCommentControllerTest). */
    private static RequestPostProcessor tokenCon(String... authorities) {
        return jwt().authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList());
    }

    private static RequestPostProcessor admin() {
        return tokenCon("SCOPE_full", "ROLE_ADMIN");
    }

    private static RequestPostProcessor usuario() {
        return tokenCon("SCOPE_full", "ROLE_USER");
    }

    // --- autorizacion -----------------------------------------------------------

    @Test
    void sin_token_los_dos_endpoints_responden_401() throws Exception {
        mockMvc.perform(get("/api/admin/logs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/search-events")).andExpect(status().isUnauthorized());

        verifyNoInteractions(listAppLogs, listUsageEvents);
    }

    @Test
    void un_usuario_sin_rol_de_administrador_recibe_403_y_no_se_lee_nada() throws Exception {
        mockMvc.perform(get("/api/admin/logs").with(usuario())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/search-events").with(usuario())).andExpect(status().isForbidden());

        verifyNoInteractions(listAppLogs, listUsageEvents);
    }

    @Test
    void un_token_de_enrolamiento_de_un_admin_tampoco_abre_los_logs() throws Exception {
        mockMvc.perform(get("/api/admin/logs").with(tokenCon("SCOPE_totp_enroll", "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    // --- /api/admin/logs ----------------------------------------------------------

    @Test
    void el_administrador_lee_los_logs() throws Exception {
        when(listAppLogs.list(null, null, null)).thenReturn(List.of(
                new AppLog(9L, AHORA, LogLevel.WARN, "com.fusaroute.X", "Algo raro", null, "main", "dev")));

        mockMvc.perform(get("/api/admin/logs").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].level").value("WARN"))
                .andExpect(jsonPath("$[0].logger").value("com.fusaroute.X"))
                .andExpect(jsonPath("$[0].message").value("Algo raro"))
                .andExpect(jsonPath("$[0].profile").value("dev"));
    }

    @Test
    void los_filtros_de_los_logs_llegan_al_caso_de_uso() throws Exception {
        when(listAppLogs.list(LogLevel.ERROR, AHORA, 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/logs")
                        .param("level", "ERROR")
                        .param("since", "2026-11-08T15:00:00Z")
                        .param("limit", "20")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(listAppLogs).list(LogLevel.ERROR, AHORA, 20);
    }

    @Test
    void un_level_inventado_responde_400() throws Exception {
        mockMvc.perform(get("/api/admin/logs").param("level", "FATAL").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void un_since_que_no_es_una_fecha_responde_400() throws Exception {
        mockMvc.perform(get("/api/admin/logs").param("since", "ayer").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void un_limit_fuera_de_rango_responde_400_con_el_motivo() throws Exception {
        when(listAppLogs.list(null, null, 501)).thenThrow(new InvalidMonitoringQueryException("limit debe estar entre 1 y 500"));

        mockMvc.perform(get("/api/admin/logs").param("limit", "501").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit debe estar entre 1 y 500"));
    }

    // --- /api/admin/search-events ---------------------------------------------------

    @Test
    void el_administrador_lee_los_eventos_de_uso_sin_ip_ni_usuario() throws Exception {
        when(listUsageEvents.list(null, null)).thenReturn(List.of(
                new UsageEvent(4L, AHORA, UsageEventType.SEARCH, "Centro", "Terminal", 12L,
                        SearchOutcome.LONG_WALK, EtaSource.ESTIMATED, 640, true)));

        mockMvc.perform(get("/api/admin/search-events").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("SEARCH"))
                .andExpect(jsonPath("$[0].originLabel").value("Centro"))
                .andExpect(jsonPath("$[0].routeId").value(12))
                .andExpect(jsonPath("$[0].outcome").value("LONG_WALK"))
                .andExpect(jsonPath("$[0].etaSource").value("ESTIMATED"))
                .andExpect(jsonPath("$[0].latencyMs").value(640))
                .andExpect(jsonPath("$[0].congestion").value(true))
                .andExpect(jsonPath("$[0].ip").doesNotExist())
                .andExpect(jsonPath("$[0].userId").doesNotExist());
    }

    @Test
    void los_filtros_de_los_eventos_llegan_al_caso_de_uso() throws Exception {
        when(listUsageEvents.list(AHORA, 5)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/search-events")
                        .param("since", "2026-11-08T15:00:00Z")
                        .param("limit", "5")
                        .with(admin()))
                .andExpect(status().isOk());

        verify(listUsageEvents).list(AHORA, 5);
    }
}
