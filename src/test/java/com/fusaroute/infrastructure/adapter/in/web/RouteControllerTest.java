package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase.RouteFare;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
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
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListActiveRoutesUseCase listActiveRoutesUseCase;

    @MockitoBean
    private GetRouteDetailUseCase getRouteDetailUseCase;

    @MockitoBean
    private GetRouteFareUseCase getRouteFareUseCase;

    @MockitoBean
    private SearchRouteUseCase searchRouteUseCase;

    private static Route urbana() {
        return new Route(1L, "Camino Real - La Pampa", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("Centro", "La Pampa"),
                List.of(new Fare(null, new BigDecimal("2600.00"), 0, LocalDate.of(2026, 2, 5))),
                List.of());
    }

    private static Route intermunicipal() {
        return new Route(2L, "Fusagasugá - Pasca", RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA,
                List.of("Fusagasugá", "Alaska", "Pasca"),
                List.of(new Fare("Alaska", new BigDecimal("3550.00"), 0, LocalDate.of(2026, 2, 5)),
                        new Fare("Pasca", new BigDecimal("4300.00"), 7, LocalDate.of(2025, 1, 16))),
                List.of());
    }

    @Test
    void list_is_public_and_returns_neighborhoods_in_order_and_fares() throws Exception {
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
    void list_does_not_expose_status_or_internal_fields() throws Exception {
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of(urbana()));

        mockMvc.perform(get("/api/routes"))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].fares[0].orderIndex").doesNotExist());
    }

    @Test
    void empty_list_responds_200_with_empty_array() throws Exception {
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void detail_is_public_and_has_same_shape() throws Exception {
        when(getRouteDetailUseCase.getById(2L)).thenReturn(intermunicipal());

        mockMvc.perform(get("/api/routes/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.type").value("INTERMUNICIPAL"))
                .andExpect(jsonPath("$.neighborhoods[1]").value("Alaska"))
                .andExpect(jsonPath("$.fares[0].amount").value(3550.0));
    }

    @Test
    void non_existent_or_suspended_route_detail_responds_404() throws Exception {
        when(getRouteDetailUseCase.getById(99L)).thenThrow(new RouteNotFoundException());

        mockMvc.perform(get("/api/routes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ruta no encontrada"));
    }

    @Test
    void fare_cost_is_public_and_returns_fares_and_last_update() throws Exception {
        RouteFare fare = new RouteFare(2L, "Fusagasugá - Pasca", RouteType.INTERMUNICIPAL,
                List.of(new Fare("Alaska", new BigDecimal("3550.00"), 0, LocalDate.of(2026, 2, 5)),
                        new Fare("Pasca", new BigDecimal("4300.00"), 7, LocalDate.of(2025, 1, 16))),
                LocalDate.of(2026, 2, 5));
        when(getRouteFareUseCase.getByRouteId(2L)).thenReturn(fare);

        mockMvc.perform(get("/api/routes/2/fare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.type").value("INTERMUNICIPAL"))
                .andExpect(jsonPath("$.lastUpdatedAt").value("2026-02-05"))
                .andExpect(jsonPath("$.fares[0].referencePoint").value("Alaska"))
                .andExpect(jsonPath("$.fares[0].amount").value(3550.0))
                .andExpect(jsonPath("$.fares[1].referencePoint").value("Pasca"));
    }

    @Test
    void non_existent_or_suspended_route_fare_responds_404() throws Exception {
        when(getRouteFareUseCase.getByRouteId(99L)).thenThrow(new RouteNotFoundException());

        mockMvc.perform(get("/api/routes/99/fare"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ruta no encontrada"));
    }

    @Test
    void non_numeric_id_responds_400() throws Exception {
        mockMvc.perform(get("/api/routes/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_is_public_and_returns_route_with_estimated_time() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(new RouteSearchResult(urbana(), Duration.ofMinutes(18)));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "La Pampa")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.id").value(1))
                .andExpect(jsonPath("$.route.name").value("Camino Real - La Pampa"))
                .andExpect(jsonPath("$.estimatedMinutes").value(18));
    }

    @Test
    void search_without_candidate_routes_responds_404_with_explicit_message() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenThrow(new NoRouteAvailableException("Centro", "Pasca"));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "Pasca")
                        .param("destinationLat", "4.2900")
                        .param("destinationLng", "-74.3000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No hay rutas disponibles que conecten Centro con Pasca"));
    }

    @Test
    void search_without_required_parameters_responds_400() throws Exception {
        mockMvc.perform(get("/api/routes/search").param("originNeighborhood", "Centro"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void write_on_routes_still_requires_authentication() throws Exception {
        mockMvc.perform(post("/api/routes"))
                .andExpect(status().is(oneOf(401, 403)));
        mockMvc.perform(delete("/api/routes/1"))
                .andExpect(status().is(oneOf(401, 403)));
    }
}
