package com.fusaroute.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PublicEndpointBearerTokenResolver} decide en que rutas se mira el header
 * Authorization. Esa decision no es solo de seguridad: donde el token se ignora, el
 * controlador recibe {@code @AuthenticationPrincipal Jwt} en null, asi que cualquier
 * comportamiento "solo si hay sesion" deja de ocurrir.
 *
 * Estos tests existen porque esa diferencia es invisible en los tests de controlador:
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} escribe el SecurityContext
 * directamente y nunca pasa por este resolver, asi que un endpoint puede pasar sus
 * pruebas y aun asi no ver jamas un token en produccion.
 */
class PublicEndpointBearerTokenResolverTest {

    private static final String TOKEN = "un-token-cualquiera";

    private final PublicEndpointBearerTokenResolver resolver = new PublicEndpointBearerTokenResolver();

    private String resolve(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return resolver.resolve(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/actuator/health"})
    void el_monitoreo_no_mira_el_token(String path) {
        // Un token vencido no debe tumbar el latido que mira UptimeRobot (RNF-02).
        assertThat(resolve("GET", path)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/routes", "/api/routes/1", "/api/routes/1/fare"})
    void el_catalogo_publico_no_mira_el_token(String path) {
        // Es la razon de ser de esta clase: el interceptor del frontend adjunta el
        // token guardado, y uno vencido rompia el catalogo con un 401.
        assertThat(resolve("GET", path)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/routes/search", "/api/routes/search/offline"})
    void la_busqueda_tampoco_mira_el_token_aqui_porque_lo_hace_el_filtro_opcional(String path) {
        // La busqueda es publica pero, con sesion, tiene que anotar la consulta en el
        // historial (RF-07, SCRUM-20). Ese token NO se resuelve aqui a proposito: si
        // se resolviera, el filtro estandar rechazaria con 401 un token vencido en un
        // endpoint que debe funcionar sin sesion. Quien lo lee —y se traga el fallo—
        // es OptionalJwtAuthenticationFilter.
        assertThat(resolve("GET", path)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/users/me", "/api/users/me/history", "/api/users/me/password"})
    void las_rutas_privadas_siempre_miran_el_token(String path) {
        assertThat(resolve("GET", path)).isEqualTo(TOKEN);
    }

    @Test
    void el_registro_y_el_login_no_miran_el_token() {
        assertThat(resolve("POST", "/api/auth/register")).isNull();
        assertThat(resolve("POST", "/api/auth/login")).isNull();
    }

    @Test
    void una_escritura_sobre_rutas_si_mira_el_token() {
        // El catalogo es publico solo para GET. Un POST a /api/routes es cosa de
        // administrador y tiene que llegar autenticado.
        assertThat(resolve("POST", "/api/routes")).isEqualTo(TOKEN);
    }
}
