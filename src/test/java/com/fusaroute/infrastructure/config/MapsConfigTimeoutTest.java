package com.fusaroute.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * El presupuesto de tiempo hacia Google (T15, SCRUM-199) es una decision de diseno medida,
 * no un detalle: si alguien lo sube otra vez a 5 s, una busqueda con Google atascado vuelve
 * a costarle al usuario 7 s en vez de 4,5 s. Esta prueba lo fija por comportamiento.
 *
 * <p>El "servidor" es un {@link ServerSocket} a pelo, no {@code com.sun.net.httpserver}: ese
 * paquete es interno del JDK y Checkstyle lo prohibe.
 */
class MapsConfigTimeoutTest {

    private ServerSocket servidor;
    private Thread aceptador;
    private String baseUrl;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        baseUrl = "http://127.0.0.1:" + servidor.getLocalPort();
    }

    @AfterEach
    void apagar() throws IOException {
        servidor.close();
        if (aceptador != null) {
            aceptador.interrupt();
        }
    }

    /** Acepta la conexion y contesta con un JSON, como un Google que responde a tiempo. */
    private void responderRapido() {
        aceptador = new Thread(() -> {
            try (Socket socket = servidor.accept()) {
                socket.getInputStream().read(new byte[2048]);
                byte[] cuerpo = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                String cabecera = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                        + "Content-Length: " + cuerpo.length + "\r\nConnection: close\r\n\r\n";
                OutputStream out = socket.getOutputStream();
                out.write(cabecera.getBytes(StandardCharsets.UTF_8));
                out.write(cuerpo);
                out.flush();
            } catch (IOException e) {
                // El servidor se cerro al terminar la prueba: no hay nada que reportar.
            }
        });
        aceptador.setDaemon(true);
        aceptador.start();
    }

    @Test
    @DisplayName("corta la llamada cuando Google no responde dentro del presupuesto de lectura")
    void cortaLaLlamadaLenta() {
        // El socket acepta la conexion en el backlog pero nadie contesta: un Google atascado.
        RestClient client = new MapsConfig().googleMapsRestClient(baseUrl, 1_500, 300);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> client.get().uri("/lento").retrieve().body(String.class))
                .isInstanceOf(ResourceAccessException.class);
        Duration gastado = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(gastado).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("una respuesta dentro del presupuesto pasa sin tocarse")
    void dejaPasarLaRespuestaRapida() {
        responderRapido();
        RestClient client = new MapsConfig().googleMapsRestClient(baseUrl, 1_500, 3_000);

        String cuerpo = client.get().uri("/rapido").retrieve().body(String.class);

        assertThat(cuerpo).isEqualTo("{\"ok\":true}");
    }

    @Test
    @DisplayName("el presupuesto por defecto es el medido en T15: 1,5 s de conexion y 3 s de lectura")
    void presupuestoPorDefecto() throws Exception {
        // Los valores por defecto viven en la firma del bean (@Value). Si alguien los cambia,
        // esta prueba lo obliga a cambiar tambien el numero documentado.
        var parametros = MapsConfig.class
                .getMethod("googleMapsRestClient", String.class, int.class, int.class)
                .getParameters();

        assertThat(parametros[1].getAnnotation(Value.class).value())
                .isEqualTo("${google.routes.connect-timeout-ms:1500}");
        assertThat(parametros[2].getAnnotation(Value.class).value())
                .isEqualTo("${google.routes.read-timeout-ms:3000}");
    }
}
