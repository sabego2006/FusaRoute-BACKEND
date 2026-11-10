package com.fusaroute.infrastructure.adapter.out.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demuestra que los secretos no llegan a la tabla (SCRUM-186). Cada caso es una forma en
 * que este backend maneja de verdad un secreto: la llave de Google viaja en la URL, el
 * JWT en el encabezado Authorization, la contrasena en el JSON del login.
 */
class LogSanitizerTest {

    // Valores de prueba con la forma de los reales, ninguno es una credencial.
    private static final String GOOGLE_KEY = "AIzaSyD-FAKEFAKEFAKEFAKEFAKEFAKE1234567";
    private static final String JWT =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3Iiwicm9sZSI6IkFETUlOIn0.c2lnbmF0dXJlRkFLRQ";

    @Test
    void la_llave_de_google_en_la_url_no_sobrevive() {
        String url = "GET https://maps.googleapis.com/maps/api/distancematrix/json?origins=a&key=" + GOOGLE_KEY;

        String limpio = LogSanitizer.sanitize(url);

        assertThat(limpio).doesNotContain(GOOGLE_KEY).doesNotContain("AIza");
        assertThat(limpio).contains("origins=a");
    }

    @Test
    void la_llave_de_google_suelta_tampoco_sobrevive() {
        assertThat(LogSanitizer.sanitize("Google respondio REQUEST_DENIED para " + GOOGLE_KEY))
                .doesNotContain(GOOGLE_KEY);
    }

    @Test
    void el_jwt_no_sobrevive_ni_suelto_ni_en_un_mensaje() {
        assertThat(LogSanitizer.sanitize("Token rechazado: " + JWT)).doesNotContain(JWT).doesNotContain("eyJ");
        assertThat(LogSanitizer.sanitize(JWT)).doesNotContain("eyJ");
    }

    @Test
    void el_encabezado_authorization_con_bearer_no_sobrevive() {
        String limpio = LogSanitizer.sanitize("Peticion rechazada. Authorization: Bearer " + JWT + " desde el movil");

        assertThat(limpio).doesNotContain(JWT).doesNotContain("eyJ");
        assertThat(limpio).contains("Authorization").contains("desde el movil");
    }

    @Test
    void authorization_basic_y_en_json_tampoco_sobreviven() {
        assertThat(LogSanitizer.sanitize("Authorization: Basic dXNlcjpwYXNz")).doesNotContain("dXNlcjpwYXNz");
        assertThat(LogSanitizer.sanitize("{\"Authorization\":\"Bearer abcdefghijkl123\"}"))
                .doesNotContain("abcdefghijkl123");
    }

    @Test
    void un_bearer_suelto_no_sobrevive() {
        assertThat(LogSanitizer.sanitize("Se recibio Bearer abcdefghijklmnop1234")).doesNotContain("abcdefghijklmnop1234");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password=Secreta123",
            "password: Secreta123",
            "{\"email\":\"a@x.co\",\"password\":\"Secreta123\"}",
            "user_password=Secreta123&otro=1",
            "contrasena=Secreta123",
            "app.jwt.secret=Secreta123",
            "client_secret=Secreta123",
            "api_key=Secreta123",
            "access_token=Secreta123",
            "app.totp.encryption-key=Secreta123"
    })
    void las_contrasenas_y_secretos_con_nombre_no_sobreviven(String mensaje) {
        assertThat(LogSanitizer.sanitize(mensaje)).doesNotContain("Secreta123").contains(LogSanitizer.REDACTED);
    }

    @Test
    void las_credenciales_de_una_url_de_conexion_no_sobreviven() {
        String limpio = LogSanitizer.sanitize(
                "No conecta a jdbc:postgresql://fusaroute_dev:ClaveDeLaBase9@db.x.supabase.co:5432/postgres");

        assertThat(limpio).doesNotContain("ClaveDeLaBase9");
        assertThat(limpio).contains("fusaroute_dev").contains("db.x.supabase.co");
    }

    @Test
    void el_texto_sin_secretos_queda_igual() {
        String normal = "Candidata 'Ruta 12' sin tiempo real: tiempo de espera agotado; tokenCount=5, key de cache vacia";

        assertThat(LogSanitizer.sanitize(normal)).isEqualTo(normal);
    }

    @Test
    void el_caracter_nul_se_elimina_porque_postgres_lo_rechaza() {
        assertThat(LogSanitizer.sanitize("hola\u0000mundo")).isEqualTo("holamundo");
    }

    @Test
    void null_se_queda_null() {
        assertThat(LogSanitizer.sanitize(null)).isNull();
        assertThat(LogSanitizer.sanitizeAndTruncate(null, 10)).isNull();
    }

    @Test
    void el_recorte_respeta_el_maximo_y_deja_marca() {
        String largo = "x".repeat(5_000);

        String recortado = LogSanitizer.sanitizeAndTruncate(largo, 2_000);

        assertThat(recortado).hasSize(2_000).endsWith("[truncado]");
    }

    @Test
    void un_token_en_el_limite_del_recorte_se_limpia_antes_de_recortar() {
        // Si se recortara primero, el JWT partido ya no coincidiria con el patron.
        String texto = "a".repeat(1_950) + JWT;

        String resultado = LogSanitizer.sanitizeAndTruncate(texto, 2_000);

        assertThat(resultado).doesNotContain("eyJ");
    }
}
