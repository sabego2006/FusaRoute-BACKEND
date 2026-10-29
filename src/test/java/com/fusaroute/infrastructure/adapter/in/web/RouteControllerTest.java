package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.BoardingPoint;
import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase.RouteFare;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.RecordSearchUseCase;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
    private GetCongestionWarningUseCase getCongestionWarningUseCase;

    @MockitoBean
    private GetRouteDetailUseCase getRouteDetailUseCase;

    @MockitoBean
    private GetRouteFareUseCase getRouteFareUseCase;

    @MockitoBean
    private SearchRouteUseCase searchRouteUseCase;

    @MockitoBean
    private RecordSearchUseCase recordSearchUseCase;

    /** Por defecto no hay trancones: cada test que los necesite los declara. */
    @BeforeEach
    void sinCongestionSalvoQueElTestDigaOtraCosa() {
        when(getCongestionWarningUseCase.forRoute(any())).thenReturn(new CongestionWarning(List.of()));
    }

    private static Route urbana() {
        return new Route(1L, "Camino Real - La Pampa", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("Centro", "La Pampa"),
                List.of(new Fare(null, new BigDecimal("2600.00"), 0, LocalDate.of(2026, 2, 5))),
                List.of());
    }

    /** Misma ruta urbana, pero con su trazado GeoJSON ya cargado. */
    private static Route urbanaConTrazado() {
        return new Route(1L, "Camino Real - La Pampa", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("Centro", "La Pampa"),
                List.of(new Fare(null, new BigDecimal("2600.00"), 0, LocalDate.of(2026, 2, 5))),
                List.of(new Coordinate(4.3433, -74.3661), new Coordinate(4.3300, -74.3600)));
    }

    private static Route intermunicipal() {
        return new Route(2L, "Fusagasugá - Pasca", RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA,
                List.of("Fusagasugá", "Alaska", "Pasca"),
                List.of(new Fare("Alaska", new BigDecimal("3550.00"), 0, LocalDate.of(2026, 2, 5)),
                        new Fare("Pasca", new BigDecimal("4300.00"), 7, LocalDate.of(2025, 1, 16))),
                List.of());
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
    void el_detalle_trae_el_trazado_como_linestring_de_geojson() throws Exception {
        when(getRouteDetailUseCase.getById(1L)).thenReturn(urbanaConTrazado());

        mockMvc.perform(get("/api/routes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path.type").value("LineString"))
                // GeoJSON escribe [longitud, latitud], al reves de como se nombran al
                // hablar. Si esto se invierte, el mapa dibuja la ruta en otro continente.
                .andExpect(jsonPath("$.path.coordinates[0][0]").value(-74.3661))
                .andExpect(jsonPath("$.path.coordinates[0][1]").value(4.3433))
                .andExpect(jsonPath("$.path.coordinates[1][0]").value(-74.3600))
                .andExpect(jsonPath("$.path.coordinates.length()").value(2));
    }

    @Test
    void una_ruta_sin_trazado_responde_path_null_y_no_un_linestring_vacio() throws Exception {
        // Hoy la mayoria de las rutas sembradas no tiene GeoJSON. El cliente necesita
        // distinguir "no hay dato" de "hay una linea sin puntos", que ni siquiera seria
        // GeoJSON valido.
        when(getRouteDetailUseCase.getById(1L)).thenReturn(urbana());

        mockMvc.perform(get("/api/routes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Camino Real - La Pampa"))
                .andExpect(jsonPath("$.path").value(nullValue()));
    }

    @Test
    void el_detalle_avisa_de_los_sectores_congestionados_que_la_ruta_atraviesa() throws Exception {
        when(getRouteDetailUseCase.getById(1L)).thenReturn(urbana());
        when(getCongestionWarningUseCase.forRoute(List.of("Centro", "La Pampa")))
                .thenReturn(new CongestionWarning(List.of(
                        new TrafficSector(1L, "Centro - Carrera 1", Set.of("Centro"),
                                LocalTime.of(17, 0), LocalTime.of(19, 0)))));

        mockMvc.perform(get("/api/routes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.congestionSectors.length()").value(1))
                .andExpect(jsonPath("$.congestionSectors[0].name").value("Centro - Carrera 1"))
                .andExpect(jsonPath("$.congestionSectors[0].startsAt").value("17:00:00"))
                .andExpect(jsonPath("$.congestionSectors[0].endsAt").value("19:00:00"));
    }

    @Test
    void sin_sectores_activos_el_detalle_responde_una_lista_vacia() throws Exception {
        // Es el estado de hoy: la tabla de sectores esta vacia, asi que no hay aviso.
        when(getRouteDetailUseCase.getById(1L)).thenReturn(urbana());

        mockMvc.perform(get("/api/routes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.congestionSectors.length()").value(0));
    }

    @Test
    void el_listado_no_carga_el_trazado() throws Exception {
        // El trazado son decenas de vertices por ruta: mandarlo en el listado pesaria
        // de mas en una pantalla que no dibuja mapas (LCP < 2.5 s en 4G lento).
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of(urbanaConTrazado()));

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Camino Real - La Pampa"))
                .andExpect(jsonPath("$[0].path").doesNotExist());
    }

    @Test
    void detalle_de_ruta_inexistente_o_suspendida_responde_404() throws Exception {
        when(getRouteDetailUseCase.getById(99L)).thenThrow(new RouteNotFoundException());

        mockMvc.perform(get("/api/routes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ruta no encontrada"));
    }

    @Test
    void costo_del_pasaje_es_publico_y_devuelve_tarifas_y_ultima_actualizacion() throws Exception {
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
    void costo_del_pasaje_de_ruta_inexistente_o_suspendida_responde_404() throws Exception {
        when(getRouteFareUseCase.getByRouteId(99L)).thenThrow(new RouteNotFoundException());

        mockMvc.perform(get("/api/routes/99/fare"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ruta no encontrada"));
    }

    @Test
    void id_no_numerico_responde_400() throws Exception {
        mockMvc.perform(get("/api/routes/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void busqueda_es_publica_y_devuelve_la_ruta_con_el_tiempo_estimado() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(urbana(), Duration.ofMinutes(18),
                        new BoardingPoint(new Coordinate(4.3430, -74.3658), 240, 0),
                        new BoardingPoint(new Coordinate(4.3305, -74.3602), 90, 2_400),
                        2_400d));

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
                .andExpect(jsonPath("$.estimatedMinutes").value(18))
                .andExpect(jsonPath("$.boarding.latitude").value(4.3430))
                .andExpect(jsonPath("$.boarding.distanceInMeters").value(240))
                .andExpect(jsonPath("$.boarding.walkingMinutes").value(2))
                .andExpect(jsonPath("$.dropOff.distanceInMeters").value(90));
    }

    @Test
    void busqueda_de_ruta_sin_trazado_responde_sin_ultima_milla() throws Exception {
        // Hoy 7 de las 9 rutas sembradas no tienen GeoJSON. La sugerencia se devuelve
        // igual; lo que no se puede decir es por donde acercarse a pie.
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(urbana(), Duration.ofMinutes(18), null, null, null));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "La Pampa")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedMinutes").value(18))
                .andExpect(jsonPath("$.boarding").value(nullValue()))
                .andExpect(jsonPath("$.dropOff").value(nullValue()));
    }

    @Test
    void busqueda_con_coordenadas_fuera_de_rango_responde_400() throws Exception {
        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "91")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "La Pampa")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void la_busqueda_offline_es_publica_y_responde_sin_tiempo_estimado() throws Exception {
        when(searchRouteUseCase.findBestRouteOffline(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.offline(urbana(), 2_400d,
                        new BoardingPoint(new Coordinate(4.3430, -74.3658), 240, 0),
                        new BoardingPoint(new Coordinate(4.3305, -74.3602), 90, 2_400)));

        mockMvc.perform(get("/api/routes/search/offline")
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "La Pampa")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.name").value("Camino Real - La Pampa"))
                // Sin red no se consulta a Google, asi que no hay tiempo que dar.
                .andExpect(jsonPath("$.estimatedMinutes").value(nullValue()))
                .andExpect(jsonPath("$.travelDistanceInMeters").value(2400))
                .andExpect(jsonPath("$.boarding.distanceInMeters").value(240));
    }

    @Test
    void la_busqueda_offline_sin_candidatas_responde_404() throws Exception {
        when(searchRouteUseCase.findBestRouteOffline(any(), any(), any(), any()))
                .thenThrow(new NoRouteAvailableException("Centro", "Pasca"));

        mockMvc.perform(get("/api/routes/search/offline")
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
    void busqueda_sin_rutas_candidatas_responde_404_con_mensaje_explicito() throws Exception {
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
    void busqueda_sin_parametros_obligatorios_responde_400() throws Exception {
        mockMvc.perform(get("/api/routes/search").param("originNeighborhood", "Centro"))
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

    // --- Historial de busquedas (RF-07, SCRUM-20) ---

    @Test
    void la_busqueda_con_sesion_queda_en_el_historial() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(urbana(), Duration.ofMinutes(18), null, null, null));

        mockMvc.perform(get("/api/routes/search")
                        .with(jwt().jwt(j -> j.subject("42").claim("scope", "full")))
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "La Pampa")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isOk());

        // Se guardan los barrios, no las coordenadas: el historial sirve para repetir
        // la consulta, y la pantalla vuelve a pedir el punto exacto.
        verify(recordSearchUseCase).record(42L, "Centro", "La Pampa");
    }

    @Test
    void la_busqueda_anonima_no_escribe_historial() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(urbana(), Duration.ofMinutes(18), null, null, null));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "La Pampa")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isOk());

        verifyNoInteractions(recordSearchUseCase);
    }

    @Test
    void la_busqueda_offline_tambien_queda_en_el_historial() throws Exception {
        when(searchRouteUseCase.findBestRouteOffline(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.offline(urbanaConTrazado(), 2_400d, null, null));

        mockMvc.perform(get("/api/routes/search/offline")
                        .with(jwt().jwt(j -> j.subject("42").claim("scope", "full")))
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "La Pampa")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isOk());

        verify(recordSearchUseCase).record(42L, "Centro", "La Pampa");
    }

    @Test
    void una_busqueda_sin_ruta_disponible_no_ensucia_el_historial() throws Exception {
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenThrow(new NoRouteAvailableException("Centro", "Pasca"));

        mockMvc.perform(get("/api/routes/search")
                        .with(jwt().jwt(j -> j.subject("42").claim("scope", "full")))
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "Pasca")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isNotFound());

        // Una consulta que termino en 404 no es una busqueda que valga la pena repetir.
        verifyNoInteractions(recordSearchUseCase);
    }
}
