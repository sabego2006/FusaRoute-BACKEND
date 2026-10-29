package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.BoardingPoint;
import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteSign;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.SignLine;
import com.fusaroute.domain.model.SignPlate;
import com.fusaroute.domain.model.SignSense;
import com.fusaroute.domain.model.SignTemplate;
import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase.RouteFare;
import com.fusaroute.domain.port.in.GetRouteSignsUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
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
    private GetRouteSignsUseCase getRouteSignsUseCase;

    /**
     * Por defecto no hay trancones ni letreros: cada test que los necesite los
     * declara. Sin este stub, el mock de {@code getRouteSignsUseCase} devolveria
     * null y el controlador reventaria al armar la lista de letreros.
     */
    @BeforeEach
    void sinCongestionNiLetrerosSalvoQueElTestDigaOtraCosa() {
        when(getCongestionWarningUseCase.forRoute(any())).thenReturn(new CongestionWarning(List.of()));
        when(getRouteSignsUseCase.forRoute(any())).thenReturn(List.of());
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
    void el_listado_no_lleva_letreros_igual_que_no_lleva_trazado() throws Exception {
        // RouteResponse se queda liviano a proposito (SCRUM-176): el letrero vive en
        // el detalle y en la busqueda, nunca en las 9 filas del catalogo publico.
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of(urbana()));

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signs").doesNotExist());
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
    void el_detalle_trae_la_fecha_de_ultima_actualizacion_del_pasaje() throws Exception {
        when(getRouteDetailUseCase.getById(2L)).thenReturn(intermunicipal());

        mockMvc.perform(get("/api/routes/2"))
                .andExpect(status().isOk())
                // El maximo entre las filas (Alaska 2026-02-05, Pasca 2025-01-16), no la primera.
                .andExpect(jsonPath("$.lastUpdatedAt").value("2026-02-05"));
    }

    @Test
    void el_detalle_de_una_ruta_sin_tarifas_vigentes_responde_lastUpdatedAt_null() throws Exception {
        Route sinTarifas = new Route(1L, "Camino Real - La Pampa", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("Centro", "La Pampa"), List.of(), List.of());
        when(getRouteDetailUseCase.getById(1L)).thenReturn(sinTarifas);

        mockMvc.perform(get("/api/routes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fares.length()").value(0))
                .andExpect(jsonPath("$.lastUpdatedAt").value(nullValue()));
    }

    @Test
    void el_listado_no_trae_la_fecha_de_ultima_actualizacion() throws Exception {
        // La tarjeta del listado muestra un solo precio ("$ 2.600" o "Desde $ 3.000"); una
        // fecha suelta colgada de "Desde" pertenece al piso de varios precios, no al mostrado.
        // La fecha viaja solo en el detalle, igual que el trazado.
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of(intermunicipal()));

        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastUpdatedAt").doesNotExist());
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
    void el_detalle_trae_los_letreros_de_la_ruta_en_orden() throws Exception {
        // 'Camino Real - La Pampa' es el caso real con mas letreros (SCRUM-176, V11):
        // uno SUBIENDO y dos BAJANDO, de dos busetas distintas.
        when(getRouteDetailUseCase.getById(1L)).thenReturn(urbana());
        RouteSign subiendo = new RouteSign("LC-008", SignSense.SUBIENDO, "20260927_150814.jpg",
                "20260927_150814.jpg", LocalDate.of(2026, 9, 27),
                List.of(new SignPlate(SignTemplate.TABLA_FRANJAS, null,
                        List.of(new SignLine("CAMINO REAL", "#2F8F6A", "#E8F5EE")))));
        RouteSign bajando = new RouteSign("LC-015", SignSense.BAJANDO, "20260927_151514.jpg",
                "20260927_151514.jpg", LocalDate.of(2026, 9, 27),
                List.of(new SignPlate(SignTemplate.TABLA_FRANJAS, null,
                        List.of(new SignLine("LA PAMPA", "#FFFFFF", "#8A3A2A")))));
        when(getRouteSignsUseCase.forRoute(1L)).thenReturn(List.of(subiendo, bajando));

        mockMvc.perform(get("/api/routes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signs.length()").value(2))
                .andExpect(jsonPath("$.signs[0].fieldId").value("LC-008"))
                .andExpect(jsonPath("$.signs[0].sense").value("SUBIENDO"))
                .andExpect(jsonPath("$.signs[0].plates[0].template").value("TABLA_FRANJAS"))
                .andExpect(jsonPath("$.signs[0].plates[0].lines[0].text").value("CAMINO REAL"))
                .andExpect(jsonPath("$.signs[1].fieldId").value("LC-015"))
                .andExpect(jsonPath("$.signs[1].sense").value("BAJANDO"));
    }

    @Test
    void una_ruta_sin_evidencia_de_campo_responde_signs_vacio_y_no_null() throws Exception {
        // Es el caso de 'Fusagasugá - Pasca', 'Gran Colombia' y 'Villa Leny - SENA
        // Quebrajacho' (V11): ningun letrero fotografiado las respalda todavia.
        when(getRouteDetailUseCase.getById(2L)).thenReturn(intermunicipal());

        mockMvc.perform(get("/api/routes/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signs").isArray())
                .andExpect(jsonPath("$.signs.length()").value(0));
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
                .andExpect(jsonPath("$.route.neighborhoods[0]").value("Centro"))
                .andExpect(jsonPath("$.route.neighborhoods[1]").value("La Pampa"))
                .andExpect(jsonPath("$.estimatedMinutes").value(18))
                .andExpect(jsonPath("$.boarding.latitude").value(4.3430))
                .andExpect(jsonPath("$.boarding.distanceInMeters").value(240))
                .andExpect(jsonPath("$.boarding.walkingMinutes").value(2))
                .andExpect(jsonPath("$.dropOff.distanceInMeters").value(90))
                .andExpect(jsonPath("$.lastUpdatedAt").value("2026-02-05"));
    }

    @Test
    void busqueda_devuelve_los_barrios_completos_y_en_orden_de_recorrido() throws Exception {
        // RF-05: la respuesta lleva TODOS los barrios en orden de recorrido. El recorte a 7
        // es presentacion del frontend; el backend nunca trunca ni reordena la lista.
        Route conMuchosBarrios = new Route(2L, "Ruta larga", RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("Centro", "Balmoral", "Pekin", "Antonia Santos", "Fusacatan",
                        "Los Comuneros", "Ciudad Jardin", "Cooviprof", "Potosi"),
                List.of(new Fare(null, new BigDecimal("2600.00"), 0, LocalDate.of(2026, 2, 5))),
                List.of());
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(conMuchosBarrios, Duration.ofMinutes(25), null, null, null));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "Potosi")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.neighborhoods.length()").value(9))
                .andExpect(jsonPath("$.route.neighborhoods[0]").value("Centro"))
                .andExpect(jsonPath("$.route.neighborhoods[1]").value("Balmoral"))
                .andExpect(jsonPath("$.route.neighborhoods[4]").value("Fusacatan"))
                .andExpect(jsonPath("$.route.neighborhoods[8]").value("Potosi"));
    }

    @Test
    void busqueda_devuelve_el_trazado_para_pintar_el_mapa_del_resultado() throws Exception {
        // Que el trazado viaje en /search y no solo en /{id} evita una segunda llamada
        // para pintar el mapa: la busqueda ya decidio cual ruta sugerir.
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(urbanaConTrazado(), Duration.ofMinutes(18), null, null, null));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path.type").value("LineString"))
                .andExpect(jsonPath("$.path.coordinates[0][0]").value(-74.3661))
                .andExpect(jsonPath("$.path.coordinates[0][1]").value(4.3433));
    }

    @Test
    void busqueda_de_ruta_sin_trazado_responde_path_null() throws Exception {
        // El cliente tiene que poder distinguir "no hay dato" de "hay una linea vacia";
        // igual que RouteDetailResponse, GeoJsonLineStringResponse.from devuelve null.
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(urbana(), Duration.ofMinutes(18), null, null, null));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(nullValue()));
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
    void busqueda_trae_los_letreros_de_la_ruta_sugerida() throws Exception {
        // SCRUM-176: el letrero viaja en la busqueda, no solo en el detalle, para que
        // el resultado de busqueda (el criterio de aceptacion del issue) lo muestre.
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(urbana(), Duration.ofMinutes(18), null, null, null));
        RouteSign letrero = new RouteSign("LC-008", SignSense.SUBIENDO, "20260927_150814.jpg",
                "20260927_150814.jpg", LocalDate.of(2026, 9, 27),
                List.of(new SignPlate(SignTemplate.TABLA_FRANJAS, null,
                        List.of(new SignLine("CAMINO REAL", "#2F8F6A", "#E8F5EE")))));
        when(getRouteSignsUseCase.forRoute(1L)).thenReturn(List.of(letrero));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "La Pampa")
                        .param("destinationLat", "4.3300")
                        .param("destinationLng", "-74.3600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signs.length()").value(1))
                .andExpect(jsonPath("$.signs[0].fieldId").value("LC-008"));
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
                .andExpect(jsonPath("$.route.neighborhoods[0]").value("Centro"))
                .andExpect(jsonPath("$.route.neighborhoods[1]").value("La Pampa"))
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
    void busqueda_sin_coordenadas_responde_200_el_barrio_sigue_siendo_obligatorio() throws Exception {
        // SCRUM-130: el frontend ya no pide coordenadas a mano. Sin ellas, la busqueda
        // sigue siendo valida — solo el nombre del barrio es obligatorio.
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(urbana(), Duration.ofMinutes(18), null, null, null));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedMinutes").value(18));
    }

    @Test
    void busqueda_con_solo_coordenadas_de_origen_responde_200() throws Exception {
        // Como en el frontend real: la geolocalizacion solo cubre el origen.
        when(searchRouteUseCase.findBestRoute(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.online(urbana(), Duration.ofMinutes(18), null, null, null));

        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("originLat", "4.3433")
                        .param("originLng", "-74.3661")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedMinutes").value(18));
    }

    @Test
    void busqueda_offline_sin_coordenadas_responde_200() throws Exception {
        // El modo offline nunca manda coordenadas: sin red no hay geolocalizacion
        // fiable que pedir.
        when(searchRouteUseCase.findBestRouteOffline(any(), any(), any(), any()))
                .thenReturn(RouteSearchResult.offline(urbana(), null, null, null));

        mockMvc.perform(get("/api/routes/search/offline")
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedMinutes").value(nullValue()));
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
