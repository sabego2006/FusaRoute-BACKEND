package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.oneOf;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        // SecurityConfig es resource server y necesita el JwtDecoder de JwtConfig;
        // 32 bytes en Base64, el minimo que exige.
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListActiveRoutesUseCase listActiveRoutesUseCase;

    @MockitoBean
    private GetRouteDetailUseCase getRouteDetailUseCase;

    private static Route urbana() {
        return new Route(1L, "Camino Real - La Pampa", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("Centro", "La Pampa"),
                List.of(new Fare(null, new BigDecimal("2600.00"), 0, LocalDate.of(2026, 2, 5))));
    }

    private static Route intermunicipal() {
        return new Route(2L, "Fusagasugá - Pasca", RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA,
                List.of("Fusagasugá", "Alaska", "Pasca"),
                List.of(new Fare("Alaska", new BigDecimal("3550.00"), 0, LocalDate.of(2026, 2, 5)),
                        new Fare("Pasca", new BigDecimal("4300.00"), 7, LocalDate.of(2025, 1, 16))));
    }

    @Test
    void listado_es_publico_y_devuelve_barrios_en_orden_y_tarifas() throws Exception {
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of(urbana(), intermunicipal()));

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Camino Real - La Pampa"))
                .andExpect(jsonPath("$[0].type").value("URBANA"))
                .andExpect(jsonPath("$[0].neighborhoods[0]").value("Centro"))
                .andExpect(jsonPath("$[0].neighborhoods[1]").value("La Pampa"))
                .andExpect(jsonPath("$[0].fares[0].referencePoint").value((Object) null))
                .andExpect(jsonPath("$[0].fares[0].amount").value(2600.0))
                .andExpect(jsonPath("$[0].fares[0].validFrom").value("2026-02-05"))
                .andExpect(jsonPath("$[1].type").value("INTERMUNICIPAL"))
                .andExpect(jsonPath("$[1].fares[0].referencePoint").value("Alaska"))
                .andExpect(jsonPath("$[1].fares[1].referencePoint").value("Pasca"))
                .andExpect(jsonPath("$[1].fares[1].validFrom").value("2025-01-16"));
    }

    @Test
    void listado_no_expone_estado_ni_campos_internos() throws Exception {
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of(urbana()));

        mockMvc.perform(get("/api/routes"))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].fares[0].orderIndex").doesNotExist());
    }

    @Test
    void listado_vacio_responde_200_con_arreglo_vacio() throws Exception {
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void detalle_es_publico_y_tiene_la_misma_forma() throws Exception {
        when(getRouteDetailUseCase.getById(2L)).thenReturn(intermunicipal());

        mockMvc.perform(get("/api/routes/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.type").value("INTERMUNICIPAL"))
                .andExpect(jsonPath("$.neighborhoods[1]").value("Alaska"))
                .andExpect(jsonPath("$.fares[0].amount").value(3550.0));
    }

    @Test
    void detalle_de_ruta_inexistente_o_suspendida_responde_404() throws Exception {
        when(getRouteDetailUseCase.getById(99L)).thenThrow(new RouteNotFoundException());

        mockMvc.perform(get("/api/routes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ruta no encontrada"));
    }

    @Test
    void id_no_numerico_responde_400() throws Exception {
        mockMvc.perform(get("/api/routes/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void escribir_sobre_rutas_sigue_exigiendo_autenticacion() throws Exception {
        // Hoy responde 403 (aun no hay punto de entrada de autenticacion); con el JWT de
        // SCRUM-13 sera 401. Lo que se fija aqui es que el permitAll es solo para GET.
        mockMvc.perform(post("/api/routes"))
                .andExpect(status().is(oneOf(401, 403)));
        mockMvc.perform(delete("/api/routes/1"))
                .andExpect(status().is(oneOf(401, 403)));
    }
}
