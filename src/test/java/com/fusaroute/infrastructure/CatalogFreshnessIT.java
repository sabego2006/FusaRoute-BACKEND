package com.fusaroute.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF-11 y RF-12 con el catalogo en memoria (SCRUM-192), con la aplicacion entera y Postgres real: lo que el
 * administrador escribe por la API se ve en la SIGUIENTE peticion publica, sin esperar ningun tiempo de vida.
 * Cada paso primero lee (deja el catalogo cargado en memoria), despues escribe y vuelve a leer: si la
 * invalidacion fallara, la segunda lectura devolveria lo viejo y la prueba se cae.
 *
 * <p>La ruta de prueba se traza lejos del casco urbano, y cada prueba en una latitud distinta (a mas de 5 km
 * de la anterior), para que ni las rutas de la semilla ni las de otras pruebas le compitan en la busqueda:
 * asi "la busqueda la devuelve" y "la busqueda ya no la devuelve" solo dependen de esta ruta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CatalogFreshnessIT {

    private static final java.util.concurrent.atomic.AtomicInteger TRAZADOS = new java.util.concurrent.atomic.AtomicInteger();

    private double lat;
    private String search;
    private String searchOffline;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:4200");
        registry.add("app.jwt.secret", () -> "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=");
        registry.add("app.totp.encryption-key", () -> "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=");
        registry.add("app.totp.issuer", () -> "FusaRoute");
        // Sin clave de Google: la busqueda en linea usa el tiempo estimado y no sale a la red.
        registry.add("google.maps.api.key", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    private long adminId;
    private long routeId;

    @BeforeEach
    void creaElAdministradorYLaRutaDePrueba() throws Exception {
        String sufijo = Long.toString(System.nanoTime(), 36);
        lat = 4.50 + 0.05 * TRAZADOS.getAndIncrement();
        search = "/api/routes/search?originLat=" + lat + "&originLng=-74.599&destinationLat=" + lat
                + "&destinationLng=-74.581";
        searchOffline = search.replace("/search?", "/search/offline?");
        adminId = jdbc.queryForObject("""
                INSERT INTO users (name, email, password_hash, role, active)
                VALUES ('Admin', ?, '$2a$12$hash', 'ADMIN', TRUE) RETURNING id
                """, Long.class, "admin.t04." + sufijo + "@x.co");
        // Leer antes de crear deja el catalogo en memoria SIN la ruta: crearla tiene que invalidarlo.
        publicNames();
        JsonNode creada = json.readTree(mockMvc.perform(post("/api/admin/routes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Frescura " + sufijo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        routeId = creada.get("id").asLong();
        nombreActual = "Frescura " + sufijo;
    }

    private String nombreActual;

    private RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject(Long.toString(adminId)))
                .authorities(new SimpleGrantedAuthority("SCOPE_full"), new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private String cuerpo(String nombre) {
        return """
                {"name":"%s","type":"URBANA",
                 "pathGeoJson":{"type":"LineString","coordinates":[[-74.60,%s],[-74.59,%s],[-74.58,%s]]},
                 "neighborhoods":["Frescura Inicio","Frescura Fin"],
                 "fares":[{"amount":2600,"referencePoint":null}],
                 "signFile":null}
                """.formatted(nombre, lat, lat, lat);
    }

    private List<String> publicNames() throws Exception {
        JsonNode rutas = json.readTree(mockMvc.perform(get("/api/routes")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<String> nombres = new ArrayList<>();
        rutas.forEach(ruta -> nombres.add(ruta.get("name").asText()));
        return nombres;
    }

    /** La ruta esta en el catalogo, en el detalle y la devuelven las dos busquedas, con este nombre. */
    private void assertVisibleComo(String nombre) throws Exception {
        assertThat(publicNames()).contains(nombre);
        mockMvc.perform(get("/api/routes/" + routeId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nombre));
        mockMvc.perform(get(search)).andExpect(status().isOk())
                .andExpect(jsonPath("$.route.id").value(routeId))
                .andExpect(jsonPath("$.route.name").value(nombre));
        mockMvc.perform(get(searchOffline)).andExpect(status().isOk())
                .andExpect(jsonPath("$.route.id").value(routeId))
                .andExpect(jsonPath("$.route.name").value(nombre));
    }

    /** La ruta no esta en el catalogo ni en el detalle, y ninguna busqueda la devuelve. */
    private void assertOculta() throws Exception {
        assertThat(publicNames()).doesNotContain(nombreActual);
        mockMvc.perform(get("/api/routes/" + routeId)).andExpect(status().isNotFound());
        mockMvc.perform(get(search)).andExpect(status().isNotFound());
        mockMvc.perform(get(searchOffline)).andExpect(status().isNotFound());
    }

    @Test
    void una_ruta_recien_creada_aparece_en_la_siguiente_peticion() throws Exception {
        assertVisibleComo(nombreActual);
    }

    @Test
    void modificar_una_ruta_se_refleja_en_la_siguiente_peticion() throws Exception {
        assertVisibleComo(nombreActual);

        String nuevo = nombreActual + " editada";
        mockMvc.perform(put("/api/admin/routes/" + routeId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nuevo)))
                .andExpect(status().isOk());

        assertVisibleComo(nuevo);
        assertThat(publicNames()).doesNotContain(nombreActual);
    }

    @Test
    void suspender_una_ruta_la_oculta_en_la_siguiente_peticion_y_levantarla_la_devuelve() throws Exception {
        assertVisibleComo(nombreActual);

        OffsetDateTime ahora = OffsetDateTime.now();
        String suspension = mockMvc.perform(post("/api/admin/routes/" + routeId + "/suspensions").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"reason\":\"Obras\"}"
                                .formatted(ahora.minusMinutes(1), ahora.plusDays(1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertOculta();
        // La busqueda explica por que no hay ruta: la unica que servia esta suspendida.
        mockMvc.perform(get(searchOffline)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.suspendedCandidates[0].routeId").value(routeId));

        mockMvc.perform(delete("/api/admin/suspensions/" + json.readTree(suspension).get("id").asLong()).with(admin()))
                .andExpect(status().isOk());

        assertVisibleComo(nombreActual);
    }

    @Test
    void desactivar_una_ruta_la_oculta_en_la_siguiente_peticion_y_reactivarla_la_devuelve() throws Exception {
        assertVisibleComo(nombreActual);

        mockMvc.perform(delete("/api/admin/routes/" + routeId).with(admin()))
                .andExpect(status().is2xxSuccessful());

        assertOculta();

        mockMvc.perform(post("/api/admin/routes/" + routeId + "/restore").with(admin()))
                .andExpect(status().isOk());

        assertVisibleComo(nombreActual);
    }
}
