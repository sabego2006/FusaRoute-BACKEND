package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidSuspensionException;
import com.fusaroute.domain.exception.RouteConflictException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.exception.SuspensionNotFoundException;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.SuspensionFilter;
import com.fusaroute.domain.model.SuspensionState;
import com.fusaroute.domain.model.SuspensionView;
import com.fusaroute.domain.port.in.CreateRouteSuspensionCommand;
import com.fusaroute.domain.port.in.CreateRouteSuspensionUseCase;
import com.fusaroute.domain.port.in.LiftRouteSuspensionUseCase;
import com.fusaroute.domain.port.in.ListRouteSuspensionsUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de la suspension de rutas del administrador (RF-12, SCRUM-25). Como
 * {@link AdminRouteControllerTest}, se prueba con el {@code SecurityConfig} real: la proteccion de
 * {@code /api/admin/**} es justo lo que hay que verificar.
 */
@WebMvcTest(AdminSuspensionController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class AdminSuspensionControllerTest {

    private static final String CUERPO = """
            {"startsAt":"2026-11-20T08:00:00-05:00","endsAt":"2026-11-22T20:00:00-05:00","reason":"Obras en la Cra. 6"}
            """;
    private static final Instant INICIO = Instant.parse("2026-11-20T13:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-23T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateRouteSuspensionUseCase create;
    @MockitoBean
    private ListRouteSuspensionsUseCase list;
    @MockitoBean
    private LiftRouteSuspensionUseCase lift;

    /** Authorities explicitas: ver el javadoc de {@code AdminCommentControllerTest#tokenCon}. */
    private static RequestPostProcessor tokenCon(String... authorities) {
        return jwt().jwt(j -> j.subject("1")).authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList());
    }

    private static RequestPostProcessor admin() {
        return tokenCon("SCOPE_full", "ROLE_ADMIN");
    }

    private static RequestPostProcessor usuario() {
        return tokenCon("SCOPE_full", "ROLE_USER");
    }

    private static SuspensionView vista(long id, SuspensionState estado, Instant levantada) {
        return new SuspensionView(new RouteSuspension(id, 12L, "Centro - Terminal", INICIO, FIN,
                "Obras en la Cra. 6", levantada), estado);
    }

    // --- autorizacion ------------------------------------------------------------------------

    @Test
    void sin_token_los_tres_endpoints_responden_401() throws Exception {
        mockMvc.perform(post("/api/admin/routes/12/suspensions").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/suspensions")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/suspensions/7")).andExpect(status().isUnauthorized());
    }

    @Test
    void un_usuario_sin_rol_de_administrador_recibe_403_y_no_se_ejecuta_nada() throws Exception {
        mockMvc.perform(post("/api/admin/routes/12/suspensions").with(usuario())
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/suspensions").with(usuario())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/suspensions/7").with(usuario())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/suspensions").with(tokenCon("SCOPE_full"))).andExpect(status().isForbidden());

        verifyNoInteractions(create, list, lift);
    }

    // --- crear -------------------------------------------------------------------------------

    @Test
    void crear_responde_201_con_la_suspension_y_pasa_las_fechas_a_instantes_utc() throws Exception {
        when(create.create(any())).thenReturn(vista(7, SuspensionState.UPCOMING, null));

        mockMvc.perform(post("/api/admin/routes/12/suspensions").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.routeId").value(12))
                .andExpect(jsonPath("$.routeName").value("Centro - Terminal"))
                .andExpect(jsonPath("$.startsAt").value("2026-11-20T13:00:00Z"))
                .andExpect(jsonPath("$.endsAt").value("2026-11-23T01:00:00Z"))
                .andExpect(jsonPath("$.reason").value("Obras en la Cra. 6"))
                .andExpect(jsonPath("$.liftedAt").doesNotExist())
                .andExpect(jsonPath("$.state").value("UPCOMING"));

        ArgumentCaptor<CreateRouteSuspensionCommand> command = ArgumentCaptor.forClass(CreateRouteSuspensionCommand.class);
        verify(create).create(command.capture());
        assertThat(command.getValue().routeId()).isEqualTo(12L);
        // El administrador sale del claim sub del JWT.
        assertThat(command.getValue().adminId()).isEqualTo(1L);
        // 08:00 y 20:00 en -05:00 son 13:00Z y 01:00Z del dia siguiente.
        assertThat(command.getValue().startsAt()).isEqualTo(INICIO);
        assertThat(command.getValue().endsAt()).isEqualTo(FIN);
        assertThat(command.getValue().reason()).isEqualTo("Obras en la Cra. 6");
    }

    @Test
    void crear_acepta_fechas_en_utc_y_sin_motivo() throws Exception {
        when(create.create(any())).thenReturn(vista(7, SuspensionState.UPCOMING, null));

        mockMvc.perform(post("/api/admin/routes/12/suspensions").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"2026-11-20T13:00:00Z\",\"endsAt\":\"2026-11-23T01:00:00Z\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateRouteSuspensionCommand> command = ArgumentCaptor.forClass(CreateRouteSuspensionCommand.class);
        verify(create).create(command.capture());
        assertThat(command.getValue().startsAt()).isEqualTo(INICIO);
        assertThat(command.getValue().reason()).isNull();
    }

    @Test
    void una_fecha_sin_offset_es_400_por_campo_y_no_llega_al_caso_de_uso() throws Exception {
        mockMvc.perform(post("/api/admin/routes/12/suspensions").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"2026-11-20T08:00:00\",\"endsAt\":\"manana\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("startsAt"))
                .andExpect(jsonPath("$.errors[1].field").value("endsAt"));

        verifyNoInteractions(create);
    }

    @Test
    void las_reglas_de_la_ventana_son_400_con_la_lista_por_campo() throws Exception {
        when(create.create(any())).thenThrow(new InvalidSuspensionException(List.of(
                new FieldViolation("endsAt", "El fin debe estar en el futuro"),
                new FieldViolation("reason", "El motivo no puede incluir enlaces"))));

        mockMvc.perform(post("/api/admin/routes/12/suspensions").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endsAt"))
                .andExpect(jsonPath("$.errors[0].message").value("El fin debe estar en el futuro"))
                .andExpect(jsonPath("$.errors[1].field").value("reason"));
    }

    @Test
    void un_cuerpo_ausente_o_roto_es_400() throws Exception {
        mockMvc.perform(post("/api/admin/routes/12/suspensions").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{roto")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/routes/12/suspensions").with(admin())
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    void crear_sobre_una_ruta_inexistente_es_404() throws Exception {
        when(create.create(any())).thenThrow(new RouteNotFoundException());

        mockMvc.perform(post("/api/admin/routes/999/suspensions").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isNotFound());
    }

    @Test
    void crear_sobre_una_ruta_inactiva_o_con_ventana_solapada_es_409() throws Exception {
        when(create.create(any())).thenThrow(new RouteConflictException("La ventana se solapa con otra suspension de esta ruta"));

        mockMvc.perform(post("/api/admin/routes/12/suspensions").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La ventana se solapa con otra suspension de esta ruta"));
    }

    @Test
    void un_id_de_ruta_no_numerico_es_400() throws Exception {
        mockMvc.perform(post("/api/admin/routes/abc/suspensions").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isBadRequest());
    }

    // --- listar ------------------------------------------------------------------------------

    @Test
    void el_listado_sin_state_pide_all_y_devuelve_el_estado_de_cada_una() throws Exception {
        when(list.list(SuspensionFilter.ALL)).thenReturn(List.of(
                vista(7, SuspensionState.ACTIVE, null),
                vista(8, SuspensionState.LIFTED, Instant.parse("2026-11-21T10:00:00Z"))));

        mockMvc.perform(get("/api/admin/suspensions").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$[1].state").value("LIFTED"))
                .andExpect(jsonPath("$[1].liftedAt").value("2026-11-21T10:00:00Z"));
    }

    @Test
    void el_filtro_state_se_pasa_al_caso_de_uso_sin_importar_mayusculas() throws Exception {
        when(list.list(SuspensionFilter.UPCOMING)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/suspensions?state=upcoming").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/suspensions?state=Upcoming").with(admin())).andExpect(status().isOk());

        verify(list, org.mockito.Mockito.times(2)).list(SuspensionFilter.UPCOMING);
    }

    @Test
    void un_state_desconocido_es_400_con_el_campo() throws Exception {
        mockMvc.perform(get("/api/admin/suspensions?state=ayer").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("state"));

        verifyNoInteractions(list);
    }

    // --- reactivar ---------------------------------------------------------------------------

    @Test
    void reactivar_responde_200_con_la_suspension_levantada() throws Exception {
        when(lift.lift(7L)).thenReturn(vista(7, SuspensionState.LIFTED, Instant.parse("2026-11-21T10:00:00Z")));

        mockMvc.perform(delete("/api/admin/suspensions/7").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.state").value("LIFTED"))
                .andExpect(jsonPath("$.liftedAt").value("2026-11-21T10:00:00Z"));
    }

    @Test
    void reactivar_una_inexistente_es_404() throws Exception {
        when(lift.lift(999L)).thenThrow(new SuspensionNotFoundException());

        mockMvc.perform(delete("/api/admin/suspensions/999").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void reactivar_una_terminada_o_ya_levantada_es_409() throws Exception {
        when(lift.lift(7L)).thenThrow(new RouteConflictException("La suspension ya fue levantada"));

        mockMvc.perform(delete("/api/admin/suspensions/7").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La suspension ya fue levantada"));
    }
}
