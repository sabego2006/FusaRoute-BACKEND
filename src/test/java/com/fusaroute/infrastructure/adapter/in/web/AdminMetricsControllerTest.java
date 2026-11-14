package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.AdminMetrics;
import com.fusaroute.domain.model.TopRoute;
import com.fusaroute.domain.port.in.GetAdminMetricsUseCase;
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

import java.time.OffsetDateTime;
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
 * Contrato HTTP del panel de metricas (RF-13, SCRUM-26 y SCRUM-124). Como los demas controladores de
 * administrador, se prueba con el {@code SecurityConfig} real: que {@code /api/admin/**} rechace sin token y
 * sin rol es justo lo que hay que verificar.
 */
@WebMvcTest(AdminMetricsController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class AdminMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetAdminMetricsUseCase getAdminMetrics;

    /** Authorities explicitas: ver el javadoc de {@code AdminCommentControllerTest#tokenCon}. */
    private static RequestPostProcessor tokenCon(String... authorities) {
        return jwt().jwt(j -> j.subject("1")).authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList());
    }

    private static AdminMetrics metricas() {
        return new AdminMetrics(120, 37,
                List.of(new TopRoute(3L, "Centro - Terminal", 19), new TopRoute(8L, "Chinauta", 11)),
                4, 2, OffsetDateTime.parse("2026-11-13T10:15:30-05:00"), "America/Bogota", 30);
    }

    @Test
    void sin_token_responde_401() throws Exception {
        mockMvc.perform(get("/api/admin/metrics")).andExpect(status().isUnauthorized());

        verifyNoInteractions(getAdminMetrics);
    }

    @Test
    void un_usuario_sin_rol_de_administrador_recibe_403_y_no_se_calcula_nada() throws Exception {
        mockMvc.perform(get("/api/admin/metrics").with(tokenCon("SCOPE_full", "ROLE_USER")))
                .andExpect(status().isForbidden());
        // Rol de administrador pero con un token que no es de sesion completa (scope distinto de full).
        mockMvc.perform(get("/api/admin/metrics").with(tokenCon("SCOPE_full")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(getAdminMetrics);
    }

    @Test
    void el_administrador_recibe_200_con_el_cuerpo_completo_del_contrato() throws Exception {
        when(getAdminMetrics.getMetrics()).thenReturn(metricas());

        mockMvc.perform(get("/api/admin/metrics").with(tokenCon("SCOPE_full", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(120))
                .andExpect(jsonPath("$.searchesToday").value(37))
                .andExpect(jsonPath("$.topRoutes.length()").value(2))
                .andExpect(jsonPath("$.topRoutes[0].routeId").value(3))
                .andExpect(jsonPath("$.topRoutes[0].routeName").value("Centro - Terminal"))
                .andExpect(jsonPath("$.topRoutes[0].searches").value(19))
                .andExpect(jsonPath("$.topRoutes[1].routeId").value(8))
                .andExpect(jsonPath("$.topRoutes[1].routeName").value("Chinauta"))
                .andExpect(jsonPath("$.topRoutes[1].searches").value(11))
                .andExpect(jsonPath("$.pendingComments").value(4))
                .andExpect(jsonPath("$.activeSuspensions").value(2))
                .andExpect(jsonPath("$.generatedAt").value("2026-11-13T10:15:30-05:00"))
                .andExpect(jsonPath("$.timezone").value("America/Bogota"))
                .andExpect(jsonPath("$.topRoutesWindowDays").value(30));

        verify(getAdminMetrics).getMetrics();
    }

    @Test
    void el_cuerpo_tiene_exactamente_los_ocho_campos_del_contrato() throws Exception {
        when(getAdminMetrics.getMetrics()).thenReturn(metricas());

        mockMvc.perform(get("/api/admin/metrics").with(tokenCon("SCOPE_full", "ROLE_ADMIN")))
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void un_ranking_vacio_se_serializa_como_lista_vacia() throws Exception {
        when(getAdminMetrics.getMetrics()).thenReturn(new AdminMetrics(1, 0, List.of(), 0, 0,
                OffsetDateTime.parse("2026-11-13T10:15:30-05:00"), "America/Bogota", 30));

        mockMvc.perform(get("/api/admin/metrics").with(tokenCon("SCOPE_full", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topRoutes").isArray())
                .andExpect(jsonPath("$.topRoutes.length()").value(0));
    }
}
