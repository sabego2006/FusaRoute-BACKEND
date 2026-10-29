package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.TravelTimePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchRouteServiceTest {

    private static final Coordinate ORIGIN = new Coordinate(4.3433, -74.3661);
    private static final Coordinate DESTINATION = new Coordinate(4.3300, -74.3600);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private RouteRepositoryPort routeRepository;

    @Mock
    private TravelTimePort travelTimePort;

    private SearchRouteService service() {
        return new SearchRouteService(routeRepository, travelTimePort, new SimpleMeterRegistry(), CLOCK);
    }

    private static Route route(long id, String name, RouteStatus status, String... neighborhoods) {
        return new Route(id, name, RouteType.URBANA, status, List.of(neighborhoods), List.of(), List.of());
    }

    private static Route routeWithPath(long id, String name, List<Coordinate> path, String... neighborhoods) {
        return new Route(id, name, RouteType.URBANA, RouteStatus.ACTIVA, List.of(neighborhoods), List.of(), path);
    }

    /** Misma forma que {@link #route}, pero con tarifas: para el caso de RF-06 en la busqueda. */
    private static Route routeWithFares(long id, String name, RouteType type, List<Fare> fares,
                                        String... neighborhoods) {
        return new Route(id, name, type, RouteStatus.ACTIVA, List.of(neighborhoods), fares, List.of());
    }

    /** Trazado que pasa por el origen y el destino, asi la ultima milla no lo descarta. */
    private static List<Coordinate> trazadoQuePasaPorAmbos(double desvio) {
        return List.of(
                new Coordinate(ORIGIN.latitude() + desvio, ORIGIN.longitude() + desvio),
                new Coordinate(DESTINATION.latitude() + desvio, DESTINATION.longitude() + desvio));
    }

    /**
     * Trazado paralelo al de origen->destino pero corrido ~metros al norte, asi la
     * caminata hasta el abordaje es de ese orden de magnitud (un solo eje para que no
     * se dispare por encima de los 800 m). 1 grado de latitud ~ 111 km.
     */
    private static List<Coordinate> trazadoCorridoAlNorte(double metros) {
        double delta = metros / 111_000d;
        return List.of(
                new Coordinate(ORIGIN.latitude() + delta, ORIGIN.longitude()),
                new Coordinate(DESTINATION.latitude() + delta, DESTINATION.longitude()));
    }

    /**
     * Devuelve el tiempo en buseta segun el trazado de la candidata, imitando al
     * adaptador real: Google cotiza por el par de puntos (abordaje-bajada) de cada
     * ruta, que es distinto para cada trazado. Clave por {@code path} para no tener que
     * predecir las coordenadas exactas de la proyeccion.
     */
    private void stubBusTimePorTrazado(Map<List<Coordinate>, Duration> porTrazado) {
        when(travelTimePort.getTravelTime(any(), any(), any()))
                .thenAnswer(inv -> porTrazado.get(inv.<List<Coordinate>>getArgument(2)));
    }

    @Test
    void elige_la_candidata_con_menor_tiempo_estimado() {
        // Dos trazados distintos que pasan por origen y destino: en produccion cada ruta
        // tiene su propio par abordaje-bajada, asi que Google les da tiempos distintos.
        Route lenta = routeWithPath(1L, "Lenta", trazadoQuePasaPorAmbos(0), "Centro", "Terminal");
        Route rapida = routeWithPath(2L, "Rapida", trazadoCorridoAlNorte(80), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(lenta, rapida));
        stubBusTimePorTrazado(Map.of(
                lenta.path(), Duration.ofMinutes(25),
                rapida.path(), Duration.ofMinutes(12)));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.route()).isEqualTo(rapida);
        assertThat(result.estimatedTime()).isEqualTo(Duration.ofMinutes(12));
    }

    @Test
    void cronometra_el_tramo_de_abordaje_a_bajada_no_el_par_origen_destino() {
        // Guarda de la regresion que hacia pasar el test viejo mientras produccion fallaba:
        // el trazado NO pasa por el origen, asi que los puntos que se le pasan a Google
        // son la proyeccion (abordaje/bajada), no el origen/destino crudos del usuario.
        Route conTrazado = routeWithPath(1L, "Con trazado", trazadoCorridoAlNorte(300), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(conTrazado));
        when(travelTimePort.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(14));

        service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        ArgumentCaptor<Coordinate> from = ArgumentCaptor.forClass(Coordinate.class);
        ArgumentCaptor<Coordinate> to = ArgumentCaptor.forClass(Coordinate.class);
        verify(travelTimePort).getTravelTime(from.capture(), to.capture(), eq(conTrazado.path()));

        assertThat(from.getValue()).isNotEqualTo(ORIGIN);
        assertThat(to.getValue()).isNotEqualTo(DESTINATION);
        assertThat(from.getValue()).isEqualTo(conTrazado.boardingPointFor(ORIGIN).orElseThrow().point());
        assertThat(to.getValue()).isEqualTo(conTrazado.boardingPointFor(DESTINATION).orElseThrow().point());
    }

    @Test
    void la_caminata_cuenta_en_el_ranking() {
        // La lejana tiene una buseta mas rapida (19 vs 20 min) pero deja al usuario a
        // ~700 m de cada punta; a 5 km/h esa caminata la vuelve mas lenta puerta a puerta.
        Route cercana = routeWithPath(1L, "Cercana", trazadoQuePasaPorAmbos(0), "Centro", "Terminal");
        Route lejana = routeWithPath(2L, "Lejana", trazadoCorridoAlNorte(700), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(cercana, lejana));
        stubBusTimePorTrazado(Map.of(
                cercana.path(), Duration.ofMinutes(20),
                lejana.path(), Duration.ofMinutes(19)));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.route()).isEqualTo(cercana);
        assertThat(result.estimatedTime()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void el_tiempo_estimado_es_el_tramo_en_buseta_no_el_puerta_a_puerta() {
        // Con ~400 m de caminata a cada punta el puerta a puerta pasa de 14 min, pero lo
        // que se reporta es el tramo en buseta: la caminata viaja aparte en cada punto.
        Route conTrazado = routeWithPath(1L, "Con trazado", trazadoCorridoAlNorte(400), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(conTrazado));
        when(travelTimePort.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(14));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.estimatedTime()).isEqualTo(Duration.ofMinutes(14));
    }

    @Test
    void con_tiempos_iguales_gana_la_primera() {
        // Desempate determinista: a igual tiempo puerta a puerta se queda la primera que
        // devuelve el repositorio. Trazados distintos (uno con vertice intermedio sobre
        // la misma linea) para no colisionar la clave del stub, pero misma caminata ~0.
        Coordinate medio = new Coordinate(
                (ORIGIN.latitude() + DESTINATION.latitude()) / 2,
                (ORIGIN.longitude() + DESTINATION.longitude()) / 2);
        Route primera = routeWithPath(1L, "Primera", List.of(ORIGIN, DESTINATION), "Centro", "Terminal");
        Route segunda = routeWithPath(2L, "Segunda", List.of(ORIGIN, medio, DESTINATION), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(primera, segunda));
        stubBusTimePorTrazado(Map.of(
                primera.path(), Duration.ofMinutes(15),
                segunda.path(), Duration.ofMinutes(15)));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.route()).isEqualTo(primera);
    }

    @Test
    void ignora_rutas_suspendidas_y_que_no_conectan_en_el_orden_correcto() {
        Route suspendida = route(1L, "Suspendida", RouteStatus.SUSPENDIDA, "Centro", "Terminal");
        Route sentidoContrario = route(2L, "Contraria", RouteStatus.ACTIVA, "Terminal", "Centro");
        Route valida = route(3L, "Valida", RouteStatus.ACTIVA, "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(suspendida, sentidoContrario, valida));
        when(travelTimePort.getTravelTime(ORIGIN, DESTINATION, valida.path())).thenReturn(Duration.ofMinutes(10));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.route()).isEqualTo(valida);
    }

    @Test
    void sin_candidatas_lanza_no_route_available() {
        when(routeRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION)))
                .isInstanceOf(NoRouteAvailableException.class);
    }

    @Test
    void el_resultado_trae_el_punto_de_abordaje_y_el_de_bajada() {
        Route conTrazado = routeWithPath(1L, "Con trazado", trazadoQuePasaPorAmbos(0), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(conTrazado));
        when(travelTimePort.getTravelTime(ORIGIN, DESTINATION, conTrazado.path()))
                .thenReturn(Duration.ofMinutes(14));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.boarding()).isPresent();
        assertThat(result.dropOff()).isPresent();
        assertThat(result.boarding().orElseThrow().distanceInMeters()).isLessThan(50);
    }

    @Test
    void descarta_la_candidata_que_obliga_a_caminar_mas_de_ochocientos_metros() {
        // El trazado corre a ~1.1 km al norte: conecta los barrios, pero no se puede
        // llegar a el a pie desde el origen.
        Route lejana = routeWithPath(1L, "Lejana", List.of(
                new Coordinate(ORIGIN.latitude() + 0.01, ORIGIN.longitude()),
                new Coordinate(DESTINATION.latitude() + 0.01, DESTINATION.longitude())),
                "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(lejana));

        assertThatThrownBy(() -> service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION)))
                .isInstanceOf(NoRouteAvailableException.class);
    }

    @Test
    void una_ruta_sin_trazado_se_cronometra_de_origen_a_destino() {
        // Hoy 7 de las 9 rutas sembradas no tienen GeoJSON todavia. Sin trazado no hay
        // abordaje que proyectar, asi que se cronometra el par origen-destino del usuario
        // y se sugiere igual, solo que sin ultima milla.
        Route sinTrazado = route(1L, "Sin trazado", RouteStatus.ACTIVA, "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(sinTrazado));
        when(travelTimePort.getTravelTime(ORIGIN, DESTINATION, List.of()))
                .thenReturn(Duration.ofMinutes(20));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.route()).isEqualTo(sinTrazado);
        assertThat(result.estimatedTime()).isEqualTo(Duration.ofMinutes(20));
        assertThat(result.boarding()).isEmpty();
        assertThat(result.dropOff()).isEmpty();
    }

    @Test
    void offline_elige_la_de_menor_recorrido_y_no_consulta_a_google() {
        // Las dos conectan los mismos barrios y arrancan y terminan en el mismo punto;
        // la "larga" se desvia por el noroeste antes de llegar.
        Route corta = routeWithPath(1L, "Corta", List.of(ORIGIN, DESTINATION), "Centro", "Terminal");
        Route larga = routeWithPath(2L, "Larga",
                List.of(ORIGIN, new Coordinate(4.3500, -74.3800), DESTINATION), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(larga, corta));

        RouteSearchResult result = service().findBestRouteOffline("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.route()).isEqualTo(corta);
        assertThat(result.travelTime()).isEmpty();
        assertThat(result.travelDistance()).isPresent();
        // Sin red no se pregunta el tiempo: ese es el punto del modo offline.
        verifyNoInteractions(travelTimePort);
    }

    @Test
    void offline_deja_de_ultimas_las_rutas_sin_trazado() {
        // No hay con que medirlas, asi que solo ganan si ninguna otra pudo compararse.
        Route sinTrazado = route(1L, "Sin trazado", RouteStatus.ACTIVA, "Centro", "Terminal");
        Route conTrazado = routeWithPath(2L, "Con trazado", List.of(ORIGIN, DESTINATION), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(sinTrazado, conTrazado));

        RouteSearchResult result = service().findBestRouteOffline("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.route()).isEqualTo(conTrazado);
    }

    @Test
    void offline_con_solo_rutas_sin_trazado_la_sugiere_igual_pero_sin_distancia() {
        Route sinTrazado = route(1L, "Sin trazado", RouteStatus.ACTIVA, "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(sinTrazado));

        RouteSearchResult result = service().findBestRouteOffline("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION));

        assertThat(result.route()).isEqualTo(sinTrazado);
        // Vacio y no cero: no se midio nada, y un 0 se leeria como "ya estas encima".
        assertThat(result.travelDistance()).isEmpty();
    }

    @Test
    void offline_sin_candidatas_lanza_no_route_available() {
        when(routeRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service().findBestRouteOffline("Centro", Optional.of(ORIGIN), "Terminal", Optional.of(DESTINATION)))
                .isInstanceOf(NoRouteAvailableException.class);
    }

    // --- SCRUM-130: coordenadas opcionales, derivadas del nombre del barrio ---

    @Test
    void sin_coordenadas_del_cliente_deriva_el_punto_del_barrio_sobre_el_trazado() {
        // Trazado real entre dos barrios, sin coordenadas del cliente en ninguna
        // punta: el unico origen de un punto es "Centro" -> indice 0 -> fraccion 0 del
        // trazado, y "Terminal" -> indice 1 (ultimo) -> fraccion 1.
        Route conTrazado = routeWithPath(1L, "Con trazado", trazadoQuePasaPorAmbos(0), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(conTrazado));
        when(travelTimePort.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(16));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.empty(), "Terminal", Optional.empty());

        assertThat(result.route()).isEqualTo(conTrazado);
        assertThat(result.estimatedTime()).isEqualTo(Duration.ofMinutes(16));
        assertThat(result.boarding()).isPresent();
        assertThat(result.dropOff()).isPresent();
    }

    @Test
    void solo_coordenadas_de_origen_la_de_destino_se_deriva_del_barrio() {
        // Como en el frontend real: la geolocalizacion solo cubre el origen. El
        // destino se resuelve igual, solo que a partir del nombre del barrio.
        Route conTrazado = routeWithPath(1L, "Con trazado", trazadoQuePasaPorAmbos(0), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(conTrazado));
        when(travelTimePort.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(16));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.of(ORIGIN), "Terminal", Optional.empty());

        assertThat(result.route()).isEqualTo(conTrazado);
        assertThat(result.estimatedTime()).isEqualTo(Duration.ofMinutes(16));
    }

    @Test
    void sin_coordenadas_el_filtro_de_ochocientos_metros_no_aplica() {
        // Mismo trazado que "descarta_la_candidata_que_obliga_a_caminar_mas_de_
        // ochocientos_metros" (corrido 1.1 km al norte de ORIGIN/DESTINATION): con
        // esas coordenadas crudas la ruta se descartaria. Sin coordenadas del cliente,
        // el punto es el del propio trazado (distancia ~0) y la ruta se sugiere.
        Route lejanaDeOrigenCrudo = routeWithPath(1L, "Lejana", List.of(
                new Coordinate(ORIGIN.latitude() + 0.01, ORIGIN.longitude()),
                new Coordinate(DESTINATION.latitude() + 0.01, DESTINATION.longitude())),
                "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(lejanaDeOrigenCrudo));
        when(travelTimePort.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(16));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.empty(), "Terminal", Optional.empty());

        assertThat(result.route()).isEqualTo(lejanaDeOrigenCrudo);
    }

    @Test
    void sin_coordenadas_y_sin_trazado_no_hay_tiempo_conocido_pero_la_ruta_se_sugiere() {
        // 7 de las 9 rutas sembradas no tienen trazado todavia: sin el, no hay como
        // derivar un punto del nombre del barrio ni coordenadas del cliente que
        // cronometrar. La ruta se sugiere igual, solo que sin tiempo.
        Route sinTrazado = route(1L, "Sin trazado", RouteStatus.ACTIVA, "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(sinTrazado));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.empty(), "Terminal", Optional.empty());

        assertThat(result.route()).isEqualTo(sinTrazado);
        assertThat(result.travelTime()).isEmpty();
        assertThat(result.boarding()).isEmpty();
        assertThat(result.dropOff()).isEmpty();
        verifyNoInteractions(travelTimePort);
    }

    @Test
    void entre_una_candidata_con_tiempo_conocido_y_otra_sin_el_gana_la_que_si_tiene() {
        // 45 minutos es ya un viaje largo en buseta por Fusagasuga; el centinela
        // interno de la candidata sin tiempo (ver UNKNOWN_TIME_RANK) es de un dia
        // entero a proposito, para que ningun tiempo real plausible le gane.
        Route sinTrazado = route(1L, "Sin trazado", RouteStatus.ACTIVA, "Centro", "Terminal");
        Route conTrazado = routeWithPath(2L, "Con trazado", trazadoQuePasaPorAmbos(0), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(sinTrazado, conTrazado));
        when(travelTimePort.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(45));

        RouteSearchResult result = service().findBestRoute("Centro", Optional.empty(), "Terminal", Optional.empty());

        // La candidata sin tiempo real nunca debe ganarle a una que si tiene un
        // tiempo real que reportar, aunque ese tiempo sea alto.
        assertThat(result.route()).isEqualTo(conTrazado);
        assertThat(result.travelTime()).isPresent();
    }

    @Test
    void offline_sin_coordenadas_usa_el_punto_derivado_del_barrio() {
        Route conTrazado = routeWithPath(1L, "Con trazado", List.of(ORIGIN, DESTINATION), "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(conTrazado));

        RouteSearchResult result = service().findBestRouteOffline("Centro", Optional.empty(), "Terminal", Optional.empty());

        assertThat(result.route()).isEqualTo(conTrazado);
        assertThat(result.travelDistance()).isPresent();
        verifyNoInteractions(travelTimePort);
    }

    @Test
    void offline_sin_coordenadas_y_sin_trazado_no_hay_distancia_pero_se_sugiere_igual() {
        Route sinTrazado = route(1L, "Sin trazado", RouteStatus.ACTIVA, "Centro", "Terminal");
        when(routeRepository.findAll()).thenReturn(List.of(sinTrazado));

        RouteSearchResult result = service().findBestRouteOffline("Centro", Optional.empty(), "Terminal", Optional.empty());

        assertThat(result.route()).isEqualTo(sinTrazado);
        assertThat(result.travelDistance()).isEmpty();
    }

    @Test
    void la_busqueda_online_aplica_solo_las_tarifas_vigentes_a_la_ruta_sugerida() {
        // Caso de la ruta Pasca que V3 documenta: dos series de vigencia con order_index
        // solapados (Alaska aparece en las dos). Sin filtrar por vigencia, Alaska saldria
        // dos veces y fuera de orden en el resultado de busqueda.
        List<Fare> fares = List.of(
                new Fare("Alaska", new BigDecimal("3550.00"), 0, LocalDate.of(2026, 2, 5)),
                new Fare("Corregimiento", new BigDecimal("3000.00"), 0, LocalDate.of(2025, 1, 16)),
                new Fare("Alaska", new BigDecimal("3300.00"), 3, LocalDate.of(2025, 1, 16)),
                new Fare("Pasca", new BigDecimal("4300.00"), 7, LocalDate.of(2025, 1, 16)));
        Route pasca = routeWithFares(8L, "Fusagasugá - Pasca", RouteType.INTERMUNICIPAL, fares,
                "Fusagasugá", "Alaska", "Pasca");
        when(routeRepository.findAll()).thenReturn(List.of(pasca));
        when(travelTimePort.getTravelTime(ORIGIN, DESTINATION, List.of())).thenReturn(Duration.ofMinutes(40));

        RouteSearchResult result = service().findBestRoute("Fusagasugá", Optional.of(ORIGIN), "Pasca", Optional.of(DESTINATION));

        assertThat(result.route().fares()).extracting(Fare::referencePoint)
                .containsExactly("Corregimiento", "Alaska", "Pasca");
        assertThat(result.route().fares()).extracting(f -> f.amount().intValue())
                .containsExactly(3000, 3550, 4300);
        assertThat(result.route().lastFareUpdate()).contains(LocalDate.of(2026, 2, 5));
    }

    @Test
    void la_busqueda_offline_aplica_solo_las_tarifas_vigentes_a_la_ruta_sugerida() {
        // Mismo caso, pero por el camino de findBestRouteOffline: tambien debe filtrar.
        List<Fare> fares = List.of(
                new Fare("Alaska", new BigDecimal("3550.00"), 0, LocalDate.of(2026, 2, 5)),
                new Fare("Corregimiento", new BigDecimal("3000.00"), 0, LocalDate.of(2025, 1, 16)),
                new Fare("Alaska", new BigDecimal("3300.00"), 3, LocalDate.of(2025, 1, 16)),
                new Fare("Pasca", new BigDecimal("4300.00"), 7, LocalDate.of(2025, 1, 16)));
        Route pasca = routeWithFares(8L, "Fusagasugá - Pasca", RouteType.INTERMUNICIPAL, fares,
                "Fusagasugá", "Alaska", "Pasca");
        when(routeRepository.findAll()).thenReturn(List.of(pasca));

        RouteSearchResult result = service().findBestRouteOffline("Fusagasugá", Optional.of(ORIGIN), "Pasca", Optional.of(DESTINATION));

        assertThat(result.route().fares()).extracting(Fare::referencePoint)
                .containsExactly("Corregimiento", "Alaska", "Pasca");
        assertThat(result.route().lastFareUpdate()).contains(LocalDate.of(2026, 2, 5));
    }
}
