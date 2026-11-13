package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.InvalidRouteException;
import com.fusaroute.domain.exception.RouteConflictException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.AdminRoute;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.RouteDraft;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.in.CreateRouteUseCase;
import com.fusaroute.domain.port.in.DeactivateRouteUseCase;
import com.fusaroute.domain.port.in.GetAdminRouteUseCase;
import com.fusaroute.domain.port.in.ListAllRoutesUseCase;
import com.fusaroute.domain.port.in.RestoreRouteUseCase;
import com.fusaroute.domain.port.in.UpdateRouteUseCase;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del CRUD de rutas del administrador (RF-11, SCRUM-24). Igual que
 * {@link AdminCommentControllerTest}: se prueba con el {@code SecurityConfig} real, porque
 * la proteccion de {@code /api/admin/**} es justo lo que hay que verificar.
 */
@WebMvcTest(AdminRouteController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class AdminRouteControllerTest {

    private static final Instant ACTUALIZADA = Instant.parse("2026-11-12T15:00:00Z");
    private static final String TRAZADO = "{\"type\":\"LineString\",\"coordinates\":[[-74.37,4.34],[-74.36,4.35]]}";
    private static final String CUERPO = """
            {"name":"Centro - Terminal","type":"URBANA",
             "pathGeoJson":{"type":"LineString","coordinates":[[-74.37,4.34],[-74.36,4.35]]},
             "neighborhoods":["Centro","Terminal"],
             "fares":[{"amount":2600}],"signFile":"CENTRO_TERMINAL"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListAllRoutesUseCase listAll;
    @MockitoBean
    private GetAdminRouteUseCase getRoute;
    @MockitoBean
    private CreateRouteUseCase createRoute;
    @MockitoBean
    private UpdateRouteUseCase updateRoute;
    @MockitoBean
    private DeactivateRouteUseCase deactivateRoute;
    @MockitoBean
    private RestoreRouteUseCase restoreRoute;

    private static AdminRoute ruta(long id, RouteStatus estado, String trazado) {
        return new AdminRoute(id, "Centro - Terminal", RouteType.URBANA, estado, "CENTRO_TERMINAL", trazado,
                List.of("Centro", "Terminal"),
                List.of(new Fare(null, new BigDecimal("2600.00"), 0, LocalDate.of(2026, 2, 5))), ACTUALIZADA);
    }

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

    // --- autorizacion: 401 / 403 en los seis endpoints --------------------------------------

    @Test
    void sin_token_todos_los_endpoints_responden_401() throws Exception {
        mockMvc.perform(get("/api/admin/routes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/routes/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/routes").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/routes/1").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/routes/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/routes/1/restore")).andExpect(status().isUnauthorized());
    }

    @Test
    void un_usuario_sin_rol_de_administrador_recibe_403_en_todos_los_endpoints() throws Exception {
        mockMvc.perform(get("/api/admin/routes").with(usuario())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/routes/1").with(usuario())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/routes").with(usuario())
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/routes/1").with(usuario())
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/routes/1").with(usuario())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/routes/1/restore").with(usuario())).andExpect(status().isForbidden());

        verifyNoInteractions(listAll, getRoute, createRoute, updateRoute, deactivateRoute, restoreRoute);
    }

    @Test
    void un_token_sin_rol_tampoco_entra() throws Exception {
        mockMvc.perform(get("/api/admin/routes").with(tokenCon("SCOPE_full"))).andExpect(status().isForbidden());
    }

    // --- listado y detalle ------------------------------------------------------------------

    @Test
    void el_listado_devuelve_el_resumen_de_cada_ruta_en_cualquier_estado() throws Exception {
        when(listAll.listAll()).thenReturn(List.of(ruta(1, RouteStatus.ACTIVA, TRAZADO),
                ruta(2, RouteStatus.INACTIVA, null)));

        mockMvc.perform(get("/api/admin/routes").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("URBANA"))
                .andExpect(jsonPath("$[0].status").value("ACTIVA"))
                .andExpect(jsonPath("$[0].hasPath").value(true))
                .andExpect(jsonPath("$[0].neighborhoodsCount").value(2))
                .andExpect(jsonPath("$[0].currentFare").value(2600.0))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-11-12T15:00:00Z"))
                .andExpect(jsonPath("$[1].status").value("INACTIVA"))
                .andExpect(jsonPath("$[1].hasPath").value(false))
                .andExpect(jsonPath("$[0].pathGeoJson").doesNotExist());
    }

    @Test
    void el_detalle_incluye_trazado_barrios_en_orden_y_tarifas() throws Exception {
        when(getRoute.getById(1L)).thenReturn(ruta(1, RouteStatus.ACTIVA, TRAZADO));

        mockMvc.perform(get("/api/admin/routes/1").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pathGeoJson.type").value("LineString"))
                .andExpect(jsonPath("$.pathGeoJson.coordinates[1][1]").value(4.35))
                .andExpect(jsonPath("$.neighborhoods[0]").value("Centro"))
                .andExpect(jsonPath("$.neighborhoods[1]").value("Terminal"))
                .andExpect(jsonPath("$.fares[0].amount").value(2600.0))
                .andExpect(jsonPath("$.fares[0].referencePoint").doesNotExist())
                .andExpect(jsonPath("$.fares[0].validFrom").value("2026-02-05"))
                .andExpect(jsonPath("$.signFile").value("CENTRO_TERMINAL"));
    }

    @Test
    void el_detalle_de_una_ruta_sin_trazado_lo_deja_nulo() throws Exception {
        when(getRoute.getById(2L)).thenReturn(ruta(2, RouteStatus.INACTIVA, null));

        mockMvc.perform(get("/api/admin/routes/2").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pathGeoJson").doesNotExist())
                .andExpect(jsonPath("$.hasPath").value(false));
    }

    @Test
    void el_detalle_de_una_ruta_inexistente_responde_404() throws Exception {
        when(getRoute.getById(99L)).thenThrow(new RouteNotFoundException());

        mockMvc.perform(get("/api/admin/routes/99").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void un_id_no_numerico_responde_400() throws Exception {
        mockMvc.perform(get("/api/admin/routes/abc").with(admin())).andExpect(status().isBadRequest());
    }

    // --- crear ------------------------------------------------------------------------------

    @Test
    void crear_responde_201_y_traduce_el_cuerpo_al_borrador() throws Exception {
        when(createRoute.create(any())).thenReturn(ruta(8, RouteStatus.ACTIVA, TRAZADO));

        mockMvc.perform(post("/api/admin/routes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.status").value("ACTIVA"));

        ArgumentCaptor<RouteDraft> draft = ArgumentCaptor.forClass(RouteDraft.class);
        verify(createRoute).create(draft.capture());
        assertThat(draft.getValue().name()).isEqualTo("Centro - Terminal");
        assertThat(draft.getValue().type()).isEqualTo(RouteType.URBANA);
        assertThat(draft.getValue().pathType()).isEqualTo("LineString");
        assertThat(draft.getValue().pathPositions()).hasSize(2);
        assertThat(draft.getValue().neighborhoods()).containsExactly("Centro", "Terminal");
        assertThat(draft.getValue().fares()).singleElement().satisfies(f -> {
            assertThat(f.amount()).isEqualByComparingTo("2600");
            assertThat(f.referencePoint()).isNull();
        });
        assertThat(draft.getValue().signFile()).isEqualTo("CENTRO_TERMINAL");
    }

    @Test
    void crear_con_datos_invalidos_responde_400_con_errores_por_campo() throws Exception {
        when(createRoute.create(any())).thenThrow(new InvalidRouteException(List.of(
                new FieldViolation("name", "El nombre es obligatorio"),
                new FieldViolation("fares", "La ruta necesita al menos una tarifa"))));

        mockMvc.perform(post("/api/admin/routes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[1].field").value("fares"));
    }

    @Test
    void crear_con_json_malformado_o_tipo_desconocido_responde_400() throws Exception {
        mockMvc.perform(post("/api/admin/routes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/routes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"AEREA\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(createRoute);
    }

    @Test
    void crear_con_un_nombre_repetido_responde_409() throws Exception {
        when(createRoute.create(any())).thenThrow(new RouteConflictException("Ya existe una ruta con ese nombre"));

        mockMvc.perform(post("/api/admin/routes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una ruta con ese nombre"));
    }

    // --- modificar --------------------------------------------------------------------------

    @Test
    void modificar_responde_200_con_la_ruta() throws Exception {
        when(updateRoute.update(eq(8L), any())).thenReturn(ruta(8, RouteStatus.ACTIVA, TRAZADO));

        mockMvc.perform(put("/api/admin/routes/8").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8));
    }

    @Test
    void modificar_con_datos_invalidos_responde_400() throws Exception {
        when(updateRoute.update(eq(8L), any())).thenThrow(new InvalidRouteException(
                List.of(new FieldViolation("neighborhoods", "Hay barrios repetidos: Centro"))));

        mockMvc.perform(put("/api/admin/routes/8").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("neighborhoods"));
    }

    @Test
    void modificar_una_ruta_inexistente_responde_404() throws Exception {
        when(updateRoute.update(eq(99L), any())).thenThrow(new RouteNotFoundException());

        mockMvc.perform(put("/api/admin/routes/99").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNotFound());
    }

    @Test
    void modificar_con_el_nombre_de_otra_ruta_responde_409() throws Exception {
        when(updateRoute.update(eq(8L), any())).thenThrow(new RouteConflictException("Ya existe una ruta con ese nombre"));

        mockMvc.perform(put("/api/admin/routes/8").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict());
    }

    // --- eliminar y restaurar ---------------------------------------------------------------

    @Test
    void eliminar_responde_204_sin_cuerpo() throws Exception {
        mockMvc.perform(delete("/api/admin/routes/8").with(admin()))
                .andExpect(status().isNoContent());

        verify(deactivateRoute).deactivate(8L);
    }

    @Test
    void eliminar_una_ruta_inexistente_responde_404() throws Exception {
        doThrow(new RouteNotFoundException()).when(deactivateRoute).deactivate(99L);

        mockMvc.perform(delete("/api/admin/routes/99").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void restaurar_responde_200_con_la_ruta_activa() throws Exception {
        when(restoreRoute.restore(8L)).thenReturn(ruta(8, RouteStatus.ACTIVA, TRAZADO));

        mockMvc.perform(post("/api/admin/routes/8/restore").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVA"));
    }

    @Test
    void restaurar_una_ruta_que_no_estaba_inactiva_responde_409() throws Exception {
        when(restoreRoute.restore(8L)).thenThrow(new RouteConflictException("La ruta no esta inactiva"));

        mockMvc.perform(post("/api/admin/routes/8/restore").with(admin())).andExpect(status().isConflict());
    }

    @Test
    void restaurar_una_ruta_inexistente_responde_404() throws Exception {
        when(restoreRoute.restore(99L)).thenThrow(new RouteNotFoundException());

        mockMvc.perform(post("/api/admin/routes/99/restore").with(admin())).andExpect(status().isNotFound());
    }
}
