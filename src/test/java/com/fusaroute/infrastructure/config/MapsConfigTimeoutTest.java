package com.fusaroute.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * El presupuesto de tiempo hacia Google (T15, SCRUM-199) es una decision de diseno medida,
 * no un detalle: si alguien lo sube otra vez a 5 s, una busqueda con Google atascado vuelve
 * a costarle al usuario 7 s en vez de 4,5 s. Esta prueba lo fija por comportamiento, no por
 * reflexion sobre el campo privado del request factory.
 */
class MapsConfigTimeoutTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void levantarGoogleLento() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/lento", exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(cuerpo);
            }
        });
        server.createContext("/rapido", exchange -> {
            byte[] cuerpo = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(cuerpo);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void apagar() {
        server.stop(0);
    }

    @Test
    @DisplayName("corta la llamada cuando Google no responde dentro del presupuesto de lectura")
    void cortaLaLlamadaLenta() {
        RestClient client = new MapsConfig().googleMapsRestClient(baseUrl, 1_500, 300);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> client.get().uri("/lento").retrieve().body(String.class))
                .isInstanceOf(ResourceAccessException.class);
        Duration gastado = Duration.ofNanos(System.nanoTime() - inicio);

        // Se rindio cerca del presupuesto, no a los 3 s que tarda el servidor.
        assertThat(gastado).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("una respuesta dentro del presupuesto pasa sin tocarse")
    void dejaPasarLaRespuestaRapida() {
        RestClient client = new MapsConfig().googleMapsRestClient(baseUrl, 1_500, 3_000);

        String cuerpo = client.get().uri("/rapido").retrieve().body(String.class);

        assertThat(cuerpo).isEqualTo("{\"ok\":true}");
    }

    @Test
    @DisplayName("el presupuesto por defecto es el medido en T15: 1,5 s de conexion y 3 s de lectura")
    void presupuestoPorDefecto() throws Exception {
        // Los valores por defecto viven en la firma del bean (@Value). Si alguien los cambia,
        // esta prueba lo obliga a cambiar tambien el numero documentado.
        var metodo = MapsConfig.class.getMethod("googleMapsRestClient", String.class, int.class, int.class);
        var parametros = metodo.getParameters();

        assertThat(parametros[1].getAnnotation(org.springframework.beans.factory.annotation.Value.class).value())
                .isEqualTo("${google.routes.connect-timeout-ms:1500}");
        assertThat(parametros[2].getAnnotation(org.springframework.beans.factory.annotation.Value.class).value())
                .isEqualTo("${google.routes.read-timeout-ms:3000}");
    }
}
