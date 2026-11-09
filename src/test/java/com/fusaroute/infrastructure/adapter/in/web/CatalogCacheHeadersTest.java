package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.RecordSearchUseCase;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import com.fusaroute.infrastructure.config.CatalogEtagFilter;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cache HTTP del catalogo publico (RNF-01, SCRUM-127): Cache-Control y ETag solo en
 * {@code GET /api/routes} y {@code GET /api/routes/{id}}.
 *
 * <p>Lo que importa tanto como que el catalogo se cachee es que nada mas lo haga: una
 * respuesta con datos de sesion cacheada es una fuga. El contrapeso para
 * {@code /api/users/**} esta en {@code UserControllerTest}.
 */
@WebMvcTest(RouteController.class)
@Import({SecurityConfig.class, JwtConfig.class, CatalogEtagFilter.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class CatalogCacheHeadersTest {

    private static final String SPRING_SECURITY_DEFAULT = "no-store";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListActiveRoutesUseCase listActiveRoutesUseCase;
    @MockitoBean
    private GetCongestionWarningUseCase getCongestionWarningUseCase;
    @MockitoBean
    private GetRouteDetailUseCase getRouteDetailUseCase;
    @MockitoBean
    private GetRouteFareUseCase getRouteFareUseCase;
    @MockitoBean
    private SearchRouteUseCase searchRouteUseCase;
    @MockitoBean
    private RecordSearchUseCase recordSearchUseCase;

    private static Route urbana() {
        return new Route(1L, "Camino Real - La Pampa", null, RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("Centro", "La Pampa"),
                List.of(new Fare(null, new BigDecimal("2600.00"), 0, LocalDate.of(2026, 2, 5))),
                List.of());
    }

    @BeforeEach
    void catalogoConUnaRuta() {
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of(urbana()));
        when(getRouteDetailUseCase.getById(1L)).thenReturn(urbana());
        when(getCongestionWarningUseCase.forRoute(any(), any())).thenReturn(new CongestionWarning(List.of()));
    }

    @Test
    void el_listado_se_cachea_diez_minutos_con_revalidacion_en_segundo_plano() throws Exception {
        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        "max-age=600, public, stale-while-revalidate=300"))
                // Spring Security solo omite Pragma y Expires si el Cache-Control ya venia puesto;
                // si aparecen aqui, el controlador perdio y el navegador vuelve a no cachear.
                .andExpect(header().doesNotExist("Pragma"))
                .andExpect(header().doesNotExist("Expires"))
                .andExpect(header().string("ETag", not(emptyOrNullString())));
    }

    @Test
    void el_detalle_se_cachea_un_minuto_y_sin_stale_porque_el_aviso_de_trancon_depende_de_la_hora() throws Exception {
        mockMvc.perform(get("/api/routes/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, public"))
                .andExpect(header().doesNotExist("Pragma"))
                .andExpect(header().string("ETag", not(emptyOrNullString())));
    }

    @Test
    void revalidar_el_listado_con_if_none_match_devuelve_304_sin_cuerpo_y_conserva_el_cache_control() throws Exception {
        String etag = mockMvc.perform(get("/api/routes")).andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/routes").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""))
                .andExpect(header().string("ETag", etag))
                .andExpect(header().string("Cache-Control", containsString("max-age=600")));
    }

    @Test
    void revalidar_el_detalle_con_if_none_match_devuelve_304_sin_cuerpo() throws Exception {
        String etag = mockMvc.perform(get("/api/routes/1")).andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/routes/1").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void un_etag_viejo_no_da_304_sino_el_cuerpo_nuevo() throws Exception {
        mockMvc.perform(get("/api/routes").header("If-None-Match", "\"hash-de-otra-version\""))
                .andExpect(status().isOk())
                .andExpect(content().string(not(emptyOrNullString())));
    }

    @Test
    void un_cambio_en_el_catalogo_cambia_el_etag() throws Exception {
        String antes = mockMvc.perform(get("/api/routes")).andReturn().getResponse().getHeader("ETag");
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of());

        String despues = mockMvc.perform(get("/api/routes")).andReturn().getResponse().getHeader("ETag");

        org.junit.jupiter.api.Assertions.assertNotEquals(antes, despues);
    }

    @Test
    void un_error_del_detalle_no_se_cachea() throws Exception {
        when(getRouteDetailUseCase.getById(99L)).thenThrow(new RouteNotFoundException());

        mockMvc.perform(get("/api/routes/99"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString(SPRING_SECURITY_DEFAULT)))
                .andExpect(header().doesNotExist("ETag"));
    }

    @Test
    void la_busqueda_y_la_tarifa_siguen_con_no_store_y_sin_etag() throws Exception {
        when(getRouteFareUseCase.getByRouteId(anyLong()))
                .thenReturn(new GetRouteFareUseCase.RouteFare(1L, "Camino Real - La Pampa", RouteType.URBANA,
                        List.of(new Fare(null, new BigDecimal("2600.00"), 0, LocalDate.of(2026, 2, 5))),
                        LocalDate.of(2026, 2, 5)));
        // La busqueda responde 404 (sin ruta candidata): basta para ver los headers, y de paso
        // comprueba que un error tampoco queda cacheado.
        when(searchRouteUseCase.findBestRoute(any(), any()))
                .thenThrow(new NoRouteAvailableException("Centro", "La Pampa"));

        mockMvc.perform(get("/api/routes/1/fare"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString(SPRING_SECURITY_DEFAULT)))
                .andExpect(header().doesNotExist("ETag"));

        // /search tambien casa con "/api/routes/*": el filtro no puede confundirlo con un id.
        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(header().string("Cache-Control", containsString(SPRING_SECURITY_DEFAULT)))
                .andExpect(header().doesNotExist("ETag"));
    }
}
