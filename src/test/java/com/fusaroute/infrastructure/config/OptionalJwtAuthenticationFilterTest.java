package com.fusaroute.infrastructure.config;

import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.ResolvedPoint;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.GetRouteFareUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import com.fusaroute.domain.port.in.RecordSearchUseCase;
import com.fusaroute.domain.port.in.SearchRouteUseCase;
import com.fusaroute.infrastructure.adapter.in.web.RouteController;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La busqueda con un token REAL en el header Authorization (RF-07, SCRUM-20).
 *
 * Existe porque el resto de los tests de {@code RouteController} usan
 * {@code .with(jwt(...))}, que escribe el SecurityContext directamente y se salta toda
 * la cadena de filtros. Con ese atajo, el historial parecia funcionar aunque en
 * produccion el token nunca llegaba al controlador: {@code PublicEndpointBearerTokenResolver}
 * lo descarta en las rutas publicas, y la busqueda es una de ellas.
 *
 * Aqui el token se firma de verdad y entra por el header, asi que lo que se prueba es
 * lo que de verdad pasa cuando el frontend hace la peticion.
 */
@WebMvcTest(RouteController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=" + OptionalJwtAuthenticationFilterTest.SECRET
})
class OptionalJwtAuthenticationFilterTest {

    static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=";

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
        return new Route(1L, "Camino Real - La Pampa", "HOSPITAL_BATALLON_LA_PAMPA_LLANO_GRANDE",
                RouteType.URBANA, RouteStatus.ACTIVA,
                List.of("Centro", "La Pampa"),
                List.of(new Fare(null, new BigDecimal("2600.00"), 0, LocalDate.of(2026, 2, 5))),
                List.of());
    }

    /** Token HS256 firmado con el mismo secreto que el backend valida. */
    private static String token(String subject, Instant expiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("scope", "full")
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(expiry))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(Base64.getDecoder().decode(SECRET)));
        return jwt.serialize();
    }

    @BeforeEach
    void stubs() {
        when(getCongestionWarningUseCase.forRoute(any(), any())).thenReturn(new CongestionWarning(List.of()));
        when(searchRouteUseCase.findBestRoute(any(), any()))
                .thenReturn(RouteSearchResult.online(urbana(), Duration.ofMinutes(18), null, null, null)
                        .withPoints(new ResolvedPoint(null, "Centro", null), new ResolvedPoint(null, "La Pampa", null)));
    }

    @Test
    void con_un_token_valido_en_el_header_la_busqueda_SI_queda_en_el_historial() throws Exception {
        // Es el caso que fallaba: el token llegaba y el controlador no lo veia.
        mockMvc.perform(get("/api/routes/search")
                        .header("Authorization", "Bearer " + token("42", Instant.now().plusSeconds(3600)))
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk());

        verify(recordSearchUseCase).record(42L, "Centro", "La Pampa");
    }

    @Test
    void sin_token_la_busqueda_funciona_y_no_escribe_historial() throws Exception {
        mockMvc.perform(get("/api/routes/search")
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk());

        verifyNoInteractions(recordSearchUseCase);
    }

    @Test
    void con_un_token_vencido_la_busqueda_sigue_funcionando_sin_401() throws Exception {
        // La razon de ser del filtro: el interceptor del frontend adjunta el token
        // guardado en localStorage, y uno vencido (RNF-04: duran una semana) no puede
        // negarle la busqueda a nadie. Se sigue como anonimo.
        mockMvc.perform(get("/api/routes/search")
                        .header("Authorization", "Bearer " + token("42", Instant.now().minusSeconds(3600)))
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk());

        verifyNoInteractions(recordSearchUseCase);
    }

    @Test
    void con_un_token_con_firma_invalida_la_busqueda_sigue_funcionando_sin_401() throws Exception {
        // Token bien formado pero firmado con otra clave: no autentica a nadie, y
        // tampoco puede tumbar un endpoint publico.
        String ajeno = token("42", Instant.now().plusSeconds(3600));
        String manipulado = ajeno.substring(0, ajeno.lastIndexOf('.')) + ".firmaFalsa";

        mockMvc.perform(get("/api/routes/search")
                        .header("Authorization", "Bearer " + manipulado)
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk());

        verifyNoInteractions(recordSearchUseCase);
    }

    @Test
    void un_token_manipulado_no_suplanta_a_otro_usuario() throws Exception {
        // Se cambia el sub a 99 y se re-firma con una clave distinta de la del backend.
        // Si el filtro no validara la firma, el historial del usuario 99 se llenaria
        // con busquedas de un desconocido.
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("99").claim("scope", "full")
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        SignedJWT falso = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        falso.sign(new MACSigner("otra-clave-de-treinta-y-dos-bytes-exactos!!".getBytes()));

        mockMvc.perform(get("/api/routes/search")
                        .header("Authorization", "Bearer " + falso.serialize())
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk());

        verifyNoInteractions(recordSearchUseCase);
    }

    @Test
    void la_busqueda_offline_con_token_valido_tambien_queda_en_el_historial() throws Exception {
        when(searchRouteUseCase.findBestRouteOffline(any(), any()))
                .thenReturn(RouteSearchResult.offline(urbana(), 2_400d, null, null)
                        .withPoints(new ResolvedPoint(null, "Centro", null), new ResolvedPoint(null, "La Pampa", null)));

        mockMvc.perform(get("/api/routes/search/offline")
                        .header("Authorization", "Bearer " + token("42", Instant.now().plusSeconds(3600)))
                        .param("originNeighborhood", "Centro")
                        .param("destinationNeighborhood", "La Pampa"))
                .andExpect(status().isOk());

        verify(recordSearchUseCase).record(42L, "Centro", "La Pampa");
    }

    @Test
    void el_catalogo_publico_sigue_sin_romperse_con_un_token_vencido() throws Exception {
        when(listActiveRoutesUseCase.listActive()).thenReturn(List.of(urbana()));

        mockMvc.perform(get("/api/routes")
                        .header("Authorization", "Bearer " + token("42", Instant.now().minusSeconds(3600))))
                .andExpect(status().isOk());
    }
}
