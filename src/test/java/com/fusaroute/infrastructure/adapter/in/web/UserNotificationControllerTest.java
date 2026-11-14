package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.NotificationReason;
import com.fusaroute.domain.model.UserNotification;
import com.fusaroute.domain.port.in.GetUserNotificationsUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de los avisos in-app del usuario (RF-12, SCRUM-25). */
@WebMvcTest(UserNotificationController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class UserNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetUserNotificationsUseCase notifications;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor sesion(long userId) {
        return jwt().jwt(j -> j.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("SCOPE_full"), new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void sin_sesion_responde_401() throws Exception {
        mockMvc.perform(get("/api/users/me/notifications")).andExpect(status().isUnauthorized());

        verifyNoInteractions(notifications);
    }

    @Test
    void un_token_sin_scope_full_responde_403() throws Exception {
        // Por ejemplo un token de enrolamiento TOTP: autentica pero no abre la sesion completa.
        mockMvc.perform(get("/api/users/me/notifications")
                        .with(jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority("SCOPE_enroll"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notifications);
    }

    @Test
    void sin_avisos_devuelve_lista_vacia() throws Exception {
        when(notifications.notificationsOf(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/users/me/notifications").with(sesion(5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void devuelve_los_avisos_del_usuario_de_la_sesion() throws Exception {
        when(notifications.notificationsOf(5L)).thenReturn(List.of(
                new UserNotification(UserNotification.ROUTE_SUSPENDED, 7L, 12L, "Centro - Terminal",
                        Instant.parse("2026-11-20T13:00:00Z"), Instant.parse("2026-11-23T01:00:00Z"),
                        "Obras en la Cra. 6", NotificationReason.FAVORITE),
                new UserNotification(UserNotification.ROUTE_SUSPENDED, 8L, 13L, "Gaitán",
                        Instant.parse("2026-11-20T13:00:00Z"), Instant.parse("2026-11-21T01:00:00Z"),
                        null, NotificationReason.FREQUENT)));

        mockMvc.perform(get("/api/users/me/notifications").with(sesion(5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("ROUTE_SUSPENDED"))
                .andExpect(jsonPath("$[0].suspensionId").value(7))
                .andExpect(jsonPath("$[0].routeId").value(12))
                .andExpect(jsonPath("$[0].routeName").value("Centro - Terminal"))
                .andExpect(jsonPath("$[0].startsAt").value("2026-11-20T13:00:00Z"))
                .andExpect(jsonPath("$[0].endsAt").value("2026-11-23T01:00:00Z"))
                .andExpect(jsonPath("$[0].reason").value("Obras en la Cra. 6"))
                .andExpect(jsonPath("$[0].because").value("FAVORITE"))
                .andExpect(jsonPath("$[1].because").value("FREQUENT"))
                .andExpect(jsonPath("$[1].reason").doesNotExist());
    }
}
