package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidSearchPointException;
import com.fusaroute.domain.exception.NoRouteAvailableException;
import com.fusaroute.domain.exception.TravelTimeUnavailableException;
import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.EtaSource;
import com.fusaroute.domain.model.Fare;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteSearchResult;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.SearchPoint;
import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import com.fusaroute.domain.port.out.RouteSpeedPort;
import com.fusaroute.domain.port.out.TrafficSectorRepositoryPort;
import com.fusaroute.domain.port.out.TravelTimePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Busqueda por geometria (P1). El escenario es una recta este-oeste a la latitud 4.34: 0,001 grados de
 * longitud son ~111 m, lo que permite fijar las distancias de caminata con la regla.
 */
class SearchRouteServiceTest {

    private static final double LAT = 4.3400;
    /** 17:10 en Bogota (UTC-5): dentro del aviso de la franja de la tarde del Indio. */
    private static final Clock TARDE = Clock.fixed(Instant.parse("2026-11-08T22:10:00Z"), ZoneId.of("America/Bogota"));
    private static final Clock MANANA = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Bogota"));

    private static final Place OESTE = place(1, "oeste", "Oeste", LAT, -74.395, "Barrio Oeste");
    private static final Place CENTRO = place(2, "centro", "Centro", LAT, -74.375);
    private static final Place ESTE = place(3, "este", "Este", LAT, -74.360);
    /** A ~450 m al norte del trazado (4.344), cerca del centro. */
    private static final Place NORTE = place(4, "norte", "Norte", 4.3440, -74.375);
    /** A ~1.100 m al norte: pasa el umbral de 800 m pero no el de 1.500 m. */
    private static final Place LEJOS = place(5, "lejos", "Lejos", 4.3500, -74.375);
    /** A ~5,5 km: no hay ruta ni con caminata ampliada. */
    private static final Place MUY_LEJOS = place(6, "muy-lejos", "Muy lejos", 4.3900, -74.375);

    private final List<Place> places = new ArrayList<>(List.of(OESTE, CENTRO, ESTE, NORTE, LEJOS, MUY_LEJOS));
    private final List<Route> routes = new ArrayList<>();
    private final List<TrafficSector> sectors = new ArrayList<>();
    private final Map<Long, List<Double>> observations = new java.util.HashMap<>();
    private final List<Object[]> recordedSpeeds = new ArrayList<>();
    private TravelTimePort travelTime;
    private Clock clock = MANANA;

    private final PlaceRepositoryPort placeRepository = new PlaceRepositoryPort() {
        @Override
        public List<Place> findAll() {
            return places;
        }

        @Override
        public Optional<Place> findById(Long id) {
            return places.stream().filter(p -> p.id().equals(id)).findFirst();
        }
    };

    private final RouteSpeedPort speedPort = new RouteSpeedPort() {
        @Override
        public Map<Long, List<Double>> recentObservations(int perRoute) {
            return observations;
        }

        @Override
        public void record(long routeId, double observedKmh) {
            recordedSpeeds.add(new Object[] {routeId, observedKmh});
        }
    };

    @BeforeEach
    void setUp() {
        travelTime = mock(TravelTimePort.class);
    }

    private SearchRouteService service() {
        RouteRepositoryPort routeRepository = new RouteRepositoryPort() {
            @Override
            public List<Route> findAll() {
                return routes;
            }

            @Override
            public Optional<Route> findById(Long id) {
                return routes.stream().filter(r -> id.equals(r.id())).findFirst();
            }
        };
        TrafficSectorRepositoryPort sectorRepository = () -> sectors;
        return new SearchRouteService(routeRepository, placeRepository, travelTime, sectorRepository,
                speedPort, new SimpleMeterRegistry(), clock);
    }

    private static Place place(long id, String slug, String name, double lat, double lng, String... aliases) {
        return new Place(id, slug, name, new Coordinate(lat, lng), List.of(aliases));
    }

    /** Recta este-oeste de -74.40 a -74.34 (~6,6 km) con un vertice cada 0,005 grados. */
    private static List<Coordinate> pathEastward() {
        List<Coordinate> path = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            path.add(new Coordinate(LAT, -74.40 + i * 0.005));
        }
        return path;
    }

    private static List<Coordinate> reversed(List<Coordinate> path) {
        List<Coordinate> copy = new ArrayList<>(path);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private static Route route(long id, String name, RouteType type, RouteStatus status, List<Coordinate> path,
                               String... neighborhoods) {
        return new Route(id, name, null, type, status, List.of(neighborhoods), List.of(), path);
    }

    private static Route urban(long id, String name, List<Coordinate> path) {
        return route(id, name, RouteType.URBANA, RouteStatus.ACTIVA, path);
    }

    private static SearchPoint at(Place place) {
        return new SearchPoint.OfPlace(place.id());
    }

    // ------------------------------------------------------------------------------------------
    // Candidatas por geometria
    // ------------------------------------------------------------------------------------------

    @Test
    void la_ruta_que_pasa_por_ambos_lugares_en_orden_es_candidata_con_su_abordaje_y_bajada() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        RouteSearchResult result = service().findBestRouteOffline(at(OESTE), at(ESTE));

        assertThat(result.route().id()).isEqualTo(10);
        assertThat(result.boarding()).isPresent();
        assertThat(result.dropOff()).isPresent();
        assertThat(result.boarding().get().distanceAlongPathInMeters())
                .isLessThan(result.dropOff().get().distanceAlongPathInMeters());
        // OESTE esta sobre el trazado: caminata cero.
        assertThat(result.boarding().get().distanceInMeters()).isLessThan(1);
        assertThat(result.longWalk()).isFalse();
        assertThat(result.origin().label()).isEqualTo("Oeste");
        assertThat(result.destination().label()).isEqualTo("Este");
    }

    @Test
    void el_sentido_contrario_no_es_candidato_una_ruta_es_un_solo_sentido() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        assertThatThrownBy(() -> service().findBestRouteOffline(at(ESTE), at(OESTE)))
                .isInstanceOf(NoRouteAvailableException.class);
    }

    @Test
    void la_ruta_de_sentido_contrario_si_sirve_para_el_viaje_inverso() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));
        routes.add(urban(11, "Este - Oeste", reversed(pathEastward())));

        assertThat(service().findBestRouteOffline(at(ESTE), at(OESTE)).route().id()).isEqualTo(11);
        assertThat(service().findBestRouteOffline(at(OESTE), at(ESTE)).route().id()).isEqualTo(10);
    }

    @Test
    void ignora_rutas_suspendidas() {
        routes.add(route(10, "Suspendida", RouteType.URBANA, RouteStatus.SUSPENDIDA, pathEastward()));

        assertThatThrownBy(() -> service().findBestRouteOffline(at(OESTE), at(ESTE)))
                .isInstanceOf(NoRouteAvailableException.class);
    }

    @Test
    void una_ruta_que_vuelve_a_pasar_por_el_origen_despues_del_destino_sigue_siendo_candidata() {
        // Va de oeste a este y regresa: el destino (CENTRO) queda a la ida y a la vuelta, y el origen
        // (OESTE) solo al principio y al final. La pasada de la ida sirve; usar solo la mas cercana
        // podria elegir una pasada incorrecta del recorrido.
        List<Coordinate> lazo = new ArrayList<>(pathEastward());
        lazo.addAll(reversed(pathEastward()));
        routes.add(urban(10, "Lazo", lazo));

        RouteSearchResult result = service().findBestRouteOffline(at(CENTRO), at(OESTE));

        assertThat(result.route().id()).isEqualTo(10);
        assertThat(result.boarding().get().distanceAlongPathInMeters())
                .isLessThan(result.dropOff().get().distanceAlongPathInMeters());
    }

    // ------------------------------------------------------------------------------------------
    // Umbrales de caminata
    // ------------------------------------------------------------------------------------------

    @Test
    void la_caminata_se_muestra_con_el_factor_de_rodeo_1_3() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        RouteSearchResult result = service().findBestRouteOffline(at(NORTE), at(ESTE));

        double straight = result.boarding().get().distanceInMeters();
        assertThat(straight).isBetween(430d, 470d);
        assertThat(result.boarding().get().walkingDistanceInMeters()).isEqualTo(straight * 1.3);
        assertThat(result.longWalk()).isFalse();
    }

    @Test
    void sin_ruta_a_800_m_ofrece_la_mejor_con_hasta_1500_m_y_la_marca_long_walk() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        RouteSearchResult result = service().findBestRouteOffline(at(LEJOS), at(ESTE));

        assertThat(result.longWalk()).isTrue();
        assertThat(result.boarding().get().distanceInMeters()).isBetween(1000d, 1200d);
    }

    @Test
    void sin_ruta_ni_a_1500_m_responde_404_con_la_ruta_mas_cercana_a_cada_punta() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        assertThatThrownBy(() -> service().findBestRouteOffline(at(MUY_LEJOS), at(ESTE)))
                .isInstanceOfSatisfying(NoRouteAvailableException.class, e -> {
                    assertThat(e.nearestToOrigin().routeName()).isEqualTo("Oeste - Este");
                    // 5,5 km en linea recta por 1,3.
                    assertThat(e.nearestToOrigin().distanceInMeters()).isBetween(6_000L, 8_000L);
                    assertThat(e.nearestToDestination().distanceInMeters()).isLessThan(5);
                });
    }

    // ------------------------------------------------------------------------------------------
    // Ranking y tiempo
    // ------------------------------------------------------------------------------------------

    @Test
    void gana_la_ruta_de_menor_tiempo_puerta_a_puerta_la_intermunicipal_va_mas_rapido() {
        routes.add(urban(10, "Urbana", pathEastward()));
        routes.add(route(11, "Intermunicipal", RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA, pathEastward()));

        RouteSearchResult result = service().findBestRouteOffline(at(OESTE), at(ESTE));

        // Mismo trazado y misma caminata: gana la de 30 km/h sobre la de 15 km/h.
        assertThat(result.route().id()).isEqualTo(11);
    }

    @Test
    void el_tiempo_estimado_es_recorrido_sobre_velocidad_redondeado_hacia_arriba() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        RouteSearchResult result = service().findBestRouteOffline(at(OESTE), at(ESTE));

        // 3.885 m aprox. a 15 km/h = 15,5 min -> 16.
        double ride = result.travelDistanceInMeters();
        long expected = (long) Math.ceil(ride / (15_000d / 60));
        assertThat(result.estimatedTime()).isEqualTo(Duration.ofMinutes(expected));
        assertThat(result.etaSource()).isEqualTo(EtaSource.ESTIMATED);
    }

    @Test
    void la_caminata_cuenta_en_el_ranking_una_ruta_un_poco_mas_lenta_pero_sin_caminata_gana() {
        // Misma recta: la ruta 10 pasa por el lugar; la 11 corre 400 m al sur (caminata de ida).
        List<Coordinate> sur = new ArrayList<>();
        pathEastward().forEach(c -> sur.add(new Coordinate(c.latitude() - 0.0036, c.longitude())));
        routes.add(route(11, "Intermunicipal al sur", RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA, sur));
        routes.add(urban(10, "Urbana directa", pathEastward()));

        RouteSearchResult result = service().findBestRouteOffline(at(OESTE), at(ESTE));

        // La intermunicipal anda al doble de velocidad pero obliga a caminar ~2 x 400 m x 1,3.
        // 3.900 m: 15 km/h = 15,6 min; 30 km/h = 7,8 min + 2 x ~7 min de caminata = ~22 min.
        assertThat(result.route().id()).isEqualTo(10);
    }

    @Test
    void la_velocidad_calibrada_con_5_o_mas_muestras_reemplaza_a_la_fija() {
        routes.add(urban(10, "Lenta en la practica", pathEastward()));
        routes.add(urban(11, "Rapida en la practica", pathEastward()));
        // La 10 mide 6 km/h, la 11 mide 25 km/h: la mediana de la lista decide.
        observations.put(10L, List.of(6d, 6d, 6d, 6d, 6d, 40d));
        observations.put(11L, List.of(25d, 25d, 25d, 25d, 25d));

        RouteSearchResult result = service().findBestRouteOffline(at(OESTE), at(ESTE));

        assertThat(result.route().id()).isEqualTo(11);
    }

    @Test
    void con_menos_de_5_muestras_no_hay_calibracion_y_se_usa_la_velocidad_fija() {
        routes.add(urban(10, "Con 4 muestras", pathEastward()));
        routes.add(route(11, "Intermunicipal", RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA, pathEastward()));
        observations.put(11L, List.of(2d, 2d, 2d, 2d));

        // Si las 4 muestras contaran, la intermunicipal iria a 2 km/h y perderia.
        assertThat(service().findBestRouteOffline(at(OESTE), at(ESTE)).route().id()).isEqualTo(11);
    }

    @Test
    void la_mediana_de_un_conjunto_par_es_el_promedio_de_los_dos_centrales() {
        assertThat(SearchRouteService.median(List.of(10d, 20d, 30d, 40d))).isEqualTo(25d);
        assertThat(SearchRouteService.median(List.of(30d, 10d, 20d))).isEqualTo(20d);
    }

    // ------------------------------------------------------------------------------------------
    // Google
    // ------------------------------------------------------------------------------------------

    @Test
    void online_cronometra_solo_a_la_ganadora_con_una_sola_llamada() {
        routes.add(urban(10, "Urbana", pathEastward()));
        routes.add(route(11, "Intermunicipal", RouteType.INTERMUNICIPAL, RouteStatus.ACTIVA, pathEastward()));
        routes.add(urban(12, "Otra urbana", pathEastward()));
        when(travelTime.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(9).plusSeconds(10));

        RouteSearchResult result = service().findBestRoute(at(OESTE), at(ESTE));

        verify(travelTime, times(1)).getTravelTime(any(), any(), any());
        assertThat(result.route().id()).isEqualTo(11);
        assertThat(result.etaSource()).isEqualTo(EtaSource.TRAFFIC);
        // 9 min 10 s se redondea hacia arriba.
        assertThat(result.estimatedTime()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void a_google_se_le_manda_el_tramo_entre_abordaje_y_bajada() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));
        when(travelTime.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(12));

        RouteSearchResult result = service().findBestRoute(at(CENTRO), at(ESTE));

        verify(travelTime).getTravelTime(
                org.mockito.ArgumentMatchers.eq(result.boarding().get().point()),
                org.mockito.ArgumentMatchers.eq(result.dropOff().get().point()),
                org.mockito.ArgumentMatchers.argThat(path -> path.size() >= 2
                        && path.get(0).equals(result.boarding().get().point())
                        && path.get(path.size() - 1).equals(result.dropOff().get().point())));
    }

    @Test
    void si_google_falla_el_tiempo_sigue_siendo_un_numero_estimado_y_la_busqueda_no_falla() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));
        when(travelTime.getTravelTime(any(), any(), any()))
                .thenThrow(new TravelTimeUnavailableException("sin llave", null));

        RouteSearchResult result = service().findBestRoute(at(OESTE), at(ESTE));

        assertThat(result.etaSource()).isEqualTo(EtaSource.ESTIMATED);
        assertThat(result.estimatedTime()).isNotNull();
        assertThat(result.estimatedTime().toMinutes()).isPositive();
    }

    @Test
    void offline_nunca_llama_a_google() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        service().findBestRouteOffline(at(OESTE), at(ESTE));

        verify(travelTime, never()).getTravelTime(any(), any(), any());
    }

    @Test
    void guarda_la_velocidad_observada_cuando_google_responde() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));
        when(travelTime.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(10));

        RouteSearchResult result = service().findBestRoute(at(OESTE), at(ESTE));

        assertThat(recordedSpeeds).hasSize(1);
        double expectedKmh = result.travelDistanceInMeters() / 600d * 3.6;
        assertThat((Double) recordedSpeeds.get(0)[1]).isCloseTo(expectedKmh, org.assertj.core.data.Offset.offset(0.01));
        assertThat(recordedSpeeds.get(0)[0]).isEqualTo(10L);
    }

    @Test
    void una_velocidad_implausible_no_se_guarda() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));
        // 3,9 km en 1 minuto = 234 km/h: dato malo.
        when(travelTime.getTravelTime(any(), any(), any())).thenReturn(Duration.ofMinutes(1));

        service().findBestRoute(at(OESTE), at(ESTE));

        assertThat(recordedSpeeds).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Congestion por circulo
    // ------------------------------------------------------------------------------------------

    private static TrafficSector indioDeLaTarde(double lat, double lng) {
        return new TrafficSector(1L, "Indio (tarde)", java.util.Set.of("Indio"),
                LocalTime.of(17, 0), LocalTime.of(19, 0), lat, lng, 450);
    }

    @Test
    void el_tramo_que_cruza_el_circulo_en_la_franja_trae_el_aviso() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));
        sectors.add(indioDeLaTarde(LAT, -74.375));
        clock = TARDE;

        RouteSearchResult result = service().findBestRouteOffline(at(OESTE), at(ESTE));

        assertThat(result.congestionSectors()).extracting(TrafficSector::name).containsExactly("Indio (tarde)");
    }

    @Test
    void fuera_de_la_franja_y_de_su_aviso_previo_no_hay_aviso() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));
        sectors.add(indioDeLaTarde(LAT, -74.375));
        clock = MANANA;

        assertThat(service().findBestRouteOffline(at(OESTE), at(ESTE)).congestionSectors()).isEmpty();
    }

    @Test
    void un_tramo_que_no_cruza_el_circulo_no_trae_aviso_aunque_la_ruta_completa_si_lo_cruce() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));
        // Indio en -74.375; el viaje va de ESTE a un punto aun mas al este: no lo toca.
        Place masAlEste = place(7, "mas-este", "Mas al este", LAT, -74.345);
        places.add(masAlEste);
        sectors.add(indioDeLaTarde(LAT, -74.375));
        clock = TARDE;

        RouteSearchResult result = service().findBestRouteOffline(at(ESTE), at(masAlEste));

        assertThat(result.congestionSectors()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Resolucion de puntas
    // ------------------------------------------------------------------------------------------

    @Test
    void una_coordenada_se_etiqueta_con_el_lugar_canonico_mas_cercano() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        RouteSearchResult result = service().findBestRouteOffline(
                new SearchPoint.OfCoordinate(new Coordinate(LAT, -74.3951)), at(ESTE));

        assertThat(result.origin().label()).isEqualTo("Cerca de Oeste");
    }

    @Test
    void un_nombre_legado_se_resuelve_por_nombre_o_alias_sin_tildes_ni_mayusculas() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        RouteSearchResult result = service().findBestRouteOffline(
                new SearchPoint.OfName("barrio OESTE"), new SearchPoint.OfName("ÉSTE"));

        assertThat(result.route().id()).isEqualTo(10);
        assertThat(result.origin().label()).isEqualTo("Oeste");
        assertThat(result.destination().label()).isEqualTo("Este");
    }

    @Test
    void un_lugar_que_no_existe_es_400_y_dice_que_punta_fallo() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        assertThatThrownBy(() -> service().findBestRouteOffline(at(OESTE), new SearchPoint.OfPlace(999)))
                .isInstanceOfSatisfying(InvalidSearchPointException.class, e -> {
                    assertThat(e.side()).isEqualTo("destination");
                    assertThat(e.getMessage()).contains("destino");
                });
    }

    @Test
    void origen_y_destino_en_el_mismo_lugar_es_400_no_un_viaje_de_cero_metros() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        assertThatThrownBy(() -> service().findBestRoute(at(OESTE), new SearchPoint.OfName("barrio oeste")))
                .isInstanceOfSatisfying(InvalidSearchPointException.class, e -> {
                    assertThat(e.side()).isEqualTo("destination");
                    assertThat(e.getMessage()).contains("mismo lugar");
                });
    }

    @Test
    void un_nombre_desconocido_sin_ruta_por_nombre_es_400_no_404() {
        routes.add(urban(10, "Oeste - Este", pathEastward()));

        assertThatThrownBy(() -> service().findBestRouteOffline(new SearchPoint.OfName("Narnia"), at(ESTE)))
                .isInstanceOfSatisfying(InvalidSearchPointException.class, e -> assertThat(e.side()).isEqualTo("origin"));
    }

    @Test
    void un_nombre_que_corresponde_a_varios_lugares_es_400() {
        places.add(place(8, "oeste-2", "Oeste (otro)", 4.40, -74.30, "Barrio Oeste"));

        assertThatThrownBy(() -> service().findBestRouteOffline(new SearchPoint.OfName("Barrio Oeste"), at(ESTE)))
                .isInstanceOf(InvalidSearchPointException.class)
                .hasMessageContaining("varios lugares");
    }

    // ------------------------------------------------------------------------------------------
    // Rutas sin trazado: camino legado por nombre
    // ------------------------------------------------------------------------------------------

    @Test
    void una_ruta_sin_trazado_se_encuentra_por_nombre_pero_sin_abordaje_ni_tiempo() {
        Route sinTrazado = route(20, "Sin trazado", RouteType.URBANA, RouteStatus.ACTIVA, List.of(),
                "Barrio Oeste", "Algo", "Este");
        routes.add(sinTrazado);

        RouteSearchResult result = service().findBestRoute(at(OESTE), at(ESTE));

        assertThat(result.route().id()).isEqualTo(20);
        assertThat(result.boarding()).isEmpty();
        assertThat(result.dropOff()).isEmpty();
        assertThat(result.travelTime()).isEmpty();
        verify(travelTime, never()).getTravelTime(any(), any(), any());
    }

    @Test
    void una_ruta_sin_trazado_nunca_le_gana_a_una_con_trazado() {
        routes.add(route(20, "Sin trazado", RouteType.URBANA, RouteStatus.ACTIVA, List.of(),
                "Oeste", "Este"));
        routes.add(urban(10, "Con trazado", pathEastward()));

        assertThat(service().findBestRouteOffline(at(OESTE), at(ESTE)).route().id()).isEqualTo(10);
    }

    @Test
    void la_ruta_sin_trazado_exige_el_orden_origen_antes_que_destino() {
        routes.add(route(20, "Sin trazado", RouteType.URBANA, RouteStatus.ACTIVA, List.of(),
                "Este", "Oeste"));

        assertThatThrownBy(() -> service().findBestRouteOffline(at(OESTE), at(ESTE)))
                .isInstanceOf(NoRouteAvailableException.class);
    }

    // ------------------------------------------------------------------------------------------
    // Tarifas
    // ------------------------------------------------------------------------------------------

    @Test
    void la_ruta_sugerida_trae_solo_las_tarifas_vigentes() {
        List<Fare> fares = List.of(
                new Fare(null, BigDecimal.valueOf(2400), 0, LocalDate.of(2026, 1, 1)),
                new Fare(null, BigDecimal.valueOf(2600), 0, LocalDate.of(2026, 6, 1)),
                new Fare(null, BigDecimal.valueOf(3000), 0, LocalDate.of(2027, 1, 1)));
        routes.add(new Route(10L, "Con tarifas", null, RouteType.URBANA, RouteStatus.ACTIVA, List.of(), fares,
                pathEastward()));

        RouteSearchResult result = service().findBestRouteOffline(at(OESTE), at(ESTE));

        assertThat(result.route().fares()).hasSize(1);
        assertThat(result.route().fares().get(0).amount()).isEqualByComparingTo("2600");
    }
}
