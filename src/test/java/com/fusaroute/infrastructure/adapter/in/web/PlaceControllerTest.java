package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.port.in.ListPlacesUseCase;
import com.fusaroute.domain.port.in.ListPlacesUseCase.PlaceSummary;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/places: publico, con el contrato del selector y cache revalidable por ETag. */
@WebMvcTest(PlaceController.class)
@Import({SecurityConfig.class, JwtConfig.class, com.fusaroute.infrastructure.config.CatalogEtagFilter.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListPlacesUseCase listPlacesUseCase;

    private void stubPlaces() {
        when(listPlacesUseCase.listPlaces()).thenReturn(List.of(
                new PlaceSummary(new Place(12L, "monumento-el-indio", "Monumento El Indio",
                        new Coordinate(4.331682, -74.377517), List.of("Indio")), 19)));
    }

    @Test
    void es_publico_y_devuelve_el_contrato_del_selector() throws Exception {
        stubPlaces();

        mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[0].slug").value("monumento-el-indio"))
                .andExpect(jsonPath("$[0].name").value("Monumento El Indio"))
                .andExpect(jsonPath("$[0].lat").value(4.331682))
                .andExpect(jsonPath("$[0].lng").value(-74.377517))
                .andExpect(jsonPath("$[0].aliases[0]").value("Indio"))
                .andExpect(jsonPath("$[0].routeCount").value(19));
    }

    @Test
    void es_cacheable_y_lleva_etag() throws Exception {
        stubPlaces();

        mockMvc.perform(get("/api/places"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=600")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
                .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void con_if_none_match_igual_al_etag_responde_304() throws Exception {
        stubPlaces();
        String etag = mockMvc.perform(get("/api/places")).andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/places").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }
}
