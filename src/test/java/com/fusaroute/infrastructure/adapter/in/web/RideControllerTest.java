package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.InvalidRideException;
import com.fusaroute.domain.exception.RouteNotFoundException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Ride;
import com.fusaroute.domain.port.in.GetRideHistoryUseCase;
import com.fusaroute.domain.port.in.RecordRideCommand;
import com.fusaroute.domain.port.in.RecordRideUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RideController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class RideControllerTest {

    private static final Instant SUBIDA = Instant.parse("2026-11-09T22:42:00Z");

    private static final String CUERPO = """
            {"routeId":14,"destinationLabel":"Terminal de Transporte","destinationPlaceId":31,
             "destinationLat":4.3459,"destinationLng":-74.3779,"originLabel":"Indio"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordRideUseCase recordRide;

    @MockitoBean
    private GetRideHistoryUseCase getRideHistory;

    private static Ride viaje() {
        return new Ride(1L, 1L, 14L, "Centro - Galeria - Terminal", "CENTRO_GALERIA_TERMINAL",
                "Terminal de Transporte", 31L, new Coordinate(4.3459, -74.3779), "Indio", SUBIDA);
    }

    @Test
    void ya_me_subi_responde_201_con_el_viaje() throws Exception {
        when(recordRide.record(any())).thenReturn(viaje());

        mockMvc.perform(post("/api/users/me/rides")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.signFile").value("CENTRO_GALERIA_TERMINAL"))
                .andExpect(jsonPath("$.boardedAt").value("2026-11-09T17:42:00-05:00"))
                .andExpect(jsonPath("$.userId").doesNotExist());

        verify(recordRide).record(new RecordRideCommand(1L, 14L, "Terminal de Transporte", 31L,
                4.3459, -74.3779, "Indio"));
    }

    @Test
    void el_historial_marca_los_viajes_de_una_ruta_inactiva_como_no_disponibles() throws Exception {
        Ride deRutaInactiva = new Ride(2L, 1L, 14L, "Centro - Galeria - Terminal", null, "Terminal", null,
                new Coordinate(4.3459, -74.3779), "Indio", SUBIDA, false);
        when(getRideHistory.ridesOf(1L)).thenReturn(List.of(viaje(), deRutaInactiva));

        mockMvc.perform(get("/api/users/me/rides")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].routeAvailable").value(true))
                .andExpect(jsonPath("$[1].routeAvailable").value(false));
    }

    @Test
    void ruta_inexistente_responde_404() throws Exception {
        when(recordRide.record(any())).thenThrow(new RouteNotFoundException());

        mockMvc.perform(post("/api/users/me/rides")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNotFound());
    }

    @Test
    void datos_invalidos_responden_400_problem_detail() throws Exception {
        when(recordRide.record(any())).thenThrow(new InvalidRideException("El destino del viaje no puede ir vacio"));

        mockMvc.perform(post("/api/users/me/rides")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El destino del viaje no puede ir vacio"));
    }

    @Test
    void ya_me_subi_exige_sesion() throws Exception {
        mockMvc.perform(post("/api/users/me/rides").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historial_devuelve_los_viajes_del_usuario() throws Exception {
        when(getRideHistory.ridesOf(1L)).thenReturn(List.of(viaje()));

        mockMvc.perform(get("/api/users/me/rides")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].destinationLabel").value("Terminal de Transporte"))
                .andExpect(jsonPath("$[0].routeName").value("Centro - Galeria - Terminal"));
    }

    @Test
    void historial_exige_sesion() throws Exception {
        mockMvc.perform(get("/api/users/me/rides")).andExpect(status().isUnauthorized());
    }
}
