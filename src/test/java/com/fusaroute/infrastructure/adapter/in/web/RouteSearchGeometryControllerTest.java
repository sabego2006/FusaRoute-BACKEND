package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.InvalidSearchPointException;
import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.model.BoardingPoint;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.NearestRoute;
import com.fusaroute.domain.model.ResolvedPoint;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.SearchPoint;
import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.RecordSearchUseCase;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de la busqueda por geometria (P1): las tres formas de punta y los campos nuevos. */
@WebMvcTest(RouteController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class RouteSearchGeometryControllerTest {

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

    private static Route route() {
        return new Route(14L, "Centro - Galería - Terminal", "CENTRO_GALERIA_TERMINAL", RouteType.URBANA,
                RouteStatus.ACTIVA, List.of("Centro", "Terminal"), List.of(),
                List.of(new Coordinate(4.3317, -74.3775), new Coordinate(4.3459, -74.3779)));
    }

    private static RouteSearchResult result() {
        return new RouteSearchResult(route(), Duration.ofMinutes(18), 6_115d,
                new BoardingPoint(new Coordinate(4.3317, -74.3775), 240, 0),
                new BoardingPoint(new Coordinate(4.3459, -74.3779), 73, 6_115),
                EtaSource.TRAFFIC, false,
                List.of(new TrafficSector(1L, "Indio (tarde)", Set.of("Indio"),
                        LocalTime.of(17, 0), LocalTime.of(19, 0), 4.3317, -74.3775, 450)),
                new ResolvedPoint(new Coordinate(4.3317, -74.3775), "Monumento El Indio", null),
                new ResolvedPoint(new Coordinate(4.3459, -74.3779), "Terminal de Transporte", null));
    }

    @Test
    void por_lugar_manda_un_search_point_of_place_y_responde_los_campos_del_contrato() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any())).thenReturn(result());

        mockMvc.perform(get("/api/routes/search")
                        .param("originPlaceId", "12").param("destinationPlaceId", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.signFile").value("CENTRO_GALERIA_TERMINAL"))
                .andExpect(jsonPath("$.estimatedMinutes").value(18))
                .andExpect(jsonPath("$.etaSource").value("TRAFFIC"))
                .andExpect(jsonPath("$.travelDistanceInMeters").value(6115))
                // 240 m en linea recta x 1,3 = 312 m; a 5 km/h son 3,7 -> 4 min.
                .andExpect(jsonPath("$.boarding.distanceInMeters").value(312))
                .andExpect(jsonPath("$.boarding.walkingMinutes").value(4))
                .andExpect(jsonPath("$.dropOff.distanceInMeters").value(95))
                .andExpect(jsonPath("$.dropOff.walkingMinutes").value(2))
                .andExpect(jsonPath("$.longWalk").value(false))
                .andExpect(jsonPath("$.congestionSectors[0].name").value("Indio (tarde)"))
                .andExpect(jsonPath("$.congestionSectors[0].startsAt").value("17:00:00"))
                .andExpect(jsonPath("$.origin.label").value("Monumento El Indio"))
                .andExpect(jsonPath("$.origin.lat").value(4.3317))
                .andExpect(jsonPath("$.destination.label").value("Terminal de Transporte"))
                .andExpect(jsonPath("$.path.type").value("LineString"));

        ArgumentCaptor<SearchPoint> origin = ArgumentCaptor.forClass(SearchPoint.class);
        ArgumentCaptor<SearchPoint> destination = ArgumentCaptor.forClass(SearchPoint.class);
        verify(searchRouteUseCase).findBestRoute(origin.capture(), destination.capture());
        assertThat(origin.getValue()).isEqualTo(new SearchPoint.OfPlace(12));
        assertThat(destination.getValue()).isEqualTo(new SearchPoint.OfPlace(30));
    }

    @Test
    void por_coordenadas_manda_of_coordinate_y_el_lugar_gana_si_vienen_las_dos_formas() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any())).thenReturn(result());

        mockMvc.perform(get("/api/routes/search")
                        .param("originLat", "4.3433").param("originLng", "-74.3661")
                        .param("originPlaceId", "7")
                        .param("destinationLat", "4.3300").param("destinationLng", "-74.3600"))
                .andExpect(status().isOk());

        ArgumentCaptor<SearchPoint> origin = ArgumentCaptor.forClass(SearchPoint.class);
        ArgumentCaptor<SearchPoint> destination = ArgumentCaptor.forClass(SearchPoint.class);
        verify(searchRouteUseCase).findBestRoute(origin.capture(), destination.capture());
        // placeId > lat+lng > nombre.
        assertThat(origin.getValue()).isEqualTo(new SearchPoint.OfPlace(7));
        assertThat(destination.getValue()).isEqualTo(new SearchPoint.OfCoordinate(new Coordinate(4.3300, -74.3600)));
    }

    @Test
    void por_nombre_legado_manda_of_name_tambien_en_offline() throws Exception {
        when(searchRouteUseCase.findBestRouteOffline(any(), any())).thenReturn(result());

        mockMvc.perform(get("/api/routes/search/offline")
                        .param("originNeighborhood", "Indio").param("destinationNeighborhood", "Terminal"))
                .andExpect(status().isOk());

        verify(searchRouteUseCase).findBestRouteOffline(new SearchPoint.OfName("Indio"), new SearchPoint.OfName("Terminal"));
    }

    @Test
    void una_sola_de_las_dos_coordenadas_equivale_a_no_mandar_el_punto() throws Exception {
        mockMvc.perform(get("/api/routes/search")
                        .param("originLat", "4.3433")
                        .param("destinationPlaceId", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.point").value("origin"));

        verifyNoInteractions(searchRouteUseCase);
    }

    @Test
    void sin_ninguna_forma_de_punta_es_400_y_dice_cual_falta() throws Exception {
        mockMvc.perform(get("/api/routes/search").param("originPlaceId", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.point").value("destination"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("destinationPlaceId")));
    }

    @Test
    void una_punta_que_no_se_resuelve_es_400_con_el_punto_en_la_extension() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any()))
                .thenThrow(new InvalidSearchPointException("destination", "no existe el lugar con id 999"));

        mockMvc.perform(get("/api/routes/search")
                        .param("originPlaceId", "12").param("destinationPlaceId", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.point").value("destination"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999")));
    }

    @Test
    void sin_ruta_responde_404_con_la_ruta_mas_cercana_a_cada_punta() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any())).thenThrow(new NoRouteAvailableException(
                "Mesitas", "Pasca", new NearestRoute("Cuja - Horizonte - Mesitas", 1_900), new NearestRoute("Arbeláez", 4_200)));

        mockMvc.perform(get("/api/routes/search")
                        .param("originPlaceId", "1").param("destinationPlaceId", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.nearestRoutes.origin.routeName").value("Cuja - Horizonte - Mesitas"))
                .andExpect(jsonPath("$.nearestRoutes.origin.distanceInMeters").value(1900))
                .andExpect(jsonPath("$.nearestRoutes.destination.routeName").value("Arbeláez"));
    }

    @Test
    void el_404_sin_ruta_cercana_calculada_no_inventa_la_extension() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any())).thenThrow(new NoRouteAvailableException("A", "B"));

        mockMvc.perform(get("/api/routes/search")
                        .param("originPlaceId", "1").param("destinationPlaceId", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.nearestRoutes").doesNotExist());
    }
}
