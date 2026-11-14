package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.Coordinate;
import com.fusaroute.domain.model.FavoriteDestination;
import com.fusaroute.domain.model.NotificationReason;
import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.Ride;
import com.fusaroute.domain.model.Route;
import com.fusaroute.domain.model.RouteStatus;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.RouteType;
import com.fusaroute.domain.model.UserNotification;
import com.fusaroute.domain.port.out.FavoriteDestinationRepositoryPort;
import com.fusaroute.domain.port.out.PlaceRepositoryPort;
import com.fusaroute.domain.port.out.RideRepositoryPort;
import com.fusaroute.domain.port.out.RouteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Avisos de ruta suspendida (RF-12). El escenario es una recta este-oeste a la latitud 4.34 (0,001 grados de
 * longitud son ~111 m) con una ruta suspendida que pasa por tres barrios.
 */
class GetUserNotificationsServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-16T15:00:00Z");
    private static final Long USUARIO = 5L;
    private static final double LAT = 4.34;

    /** Sobre el trazado de la ruta, aliasada como "Barrio Oeste". */
    private static final Place OESTE = new Place(1L, "oeste", "Oeste", new Coordinate(LAT, -74.395), List.of("Barrio Oeste"));
    /** A ~5,5 km del trazado. */
    private static final Place LEJOS = new Place(2L, "lejos", "Lejos", new Coordinate(4.39, -74.375), List.of());

    private final List<RouteSuspension> efectivas = new ArrayList<>();
    private final List<Route> rutas = new ArrayList<>();
    private final List<Ride> viajes = new ArrayList<>();
    private Optional<FavoriteDestination> favorito = Optional.empty();

    private FavoriteDestinationRepositoryPort favorites;
    private RideRepositoryPort rides;
    private RouteRepositoryPort routes;
    private PlaceRepositoryPort places;
    private GetUserNotificationsService service;

    @BeforeEach
    void setUp() {
        favorites = mock(FavoriteDestinationRepositoryPort.class);
        rides = mock(RideRepositoryPort.class);
        routes = mock(RouteRepositoryPort.class);
        places = mock(PlaceRepositoryPort.class);
        when(routes.findAll()).thenAnswer(inv -> List.copyOf(rutas));
        when(favorites.findByUser(USUARIO)).thenAnswer(inv -> favorito);
        when(rides.findRecentByUser(anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(inv -> List.copyOf(viajes));
        when(places.findAll()).thenReturn(List.of(OESTE, LEJOS));
        service = new GetUserNotificationsService(now -> efectivas.stream().filter(s -> s.isEffectiveAt(now)).toList(),
                routes, favorites, places, rides, Clock.fixed(AHORA, ZoneId.of("America/Bogota")));
    }

    private static List<Coordinate> trazado() {
        List<Coordinate> path = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            path.add(new Coordinate(LAT, -74.40 + i * 0.005));
        }
        return path;
    }

    private static Route ruta(long id, String nombre, RouteStatus estado, List<Coordinate> path, String... barrios) {
        return new Route(id, nombre, null, RouteType.URBANA, estado, List.of(barrios), List.of(), path);
    }

    private void suspendida(long id, String... barrios) {
        rutas.add(ruta(id, "Ruta " + id, RouteStatus.SUSPENDIDA, trazado(), barrios));
        efectivas.add(new RouteSuspension(100L + id, id, "Ruta " + id, AHORA.minusSeconds(3600), FIN, "Obras", null));
    }

    private void favorito(String destino) {
        favorito = Optional.of(new FavoriteDestination(USUARIO, destino, AHORA));
    }

    private void viajeEn(long ruta) {
        viajes.add(new Ride((long) viajes.size() + 1, USUARIO, ruta, "Ruta " + ruta, null, "Terminal", null,
                new Coordinate(LAT, -74.36), "Indio", AHORA.minusSeconds(60L * viajes.size())));
    }

    private List<UserNotification> avisos() {
        return service.notificationsOf(USUARIO);
    }

    // --- sin avisos --------------------------------------------------------------------------

    @Test
    void sin_suspensiones_vigentes_la_lista_es_vacia_y_no_consulta_nada_del_usuario() {
        assertThat(avisos()).isEmpty();

        verifyNoInteractions(favorites, rides, places);
    }

    @Test
    void un_usuario_sin_relacion_con_la_ruta_recibe_lista_vacia() {
        suspendida(10, "Centro", "Galería", "Terminal");
        favorito("Lejos");
        viajeEn(77);

        assertThat(avisos()).isEmpty();
    }

    @Test
    void un_usuario_sin_favorito_ni_viajes_recibe_lista_vacia() {
        suspendida(10, "Centro", "Galería", "Terminal");

        assertThat(avisos()).isEmpty();
    }

    @Test
    void una_suspension_que_aun_no_empezo_o_ya_termino_no_avisa() {
        rutas.add(ruta(10, "Ruta 10", RouteStatus.SUSPENDIDA, trazado(), "Centro"));
        efectivas.add(new RouteSuspension(1L, 10L, "Ruta 10", AHORA.plusSeconds(1), FIN, null, null)); // proxima
        efectivas.add(new RouteSuspension(2L, 10L, "Ruta 10", AHORA.minusSeconds(7200), AHORA, null, null)); // termina justo ahora
        efectivas.add(new RouteSuspension(3L, 10L, "Ruta 10", AHORA.minusSeconds(7200), FIN, null, AHORA)); // levantada
        favorito("Centro");

        assertThat(avisos()).isEmpty();
    }

    @Test
    void una_ruta_que_no_se_ve_como_suspendida_no_genera_aviso() {
        // INACTIVA (soft delete posterior a la suspension) o ACTIVA: no es un "vuelve pronto".
        rutas.add(ruta(10, "Ruta 10", RouteStatus.INACTIVA, trazado(), "Centro"));
        efectivas.add(new RouteSuspension(1L, 10L, "Ruta 10", AHORA.minusSeconds(60), FIN, null, null));
        favorito("Centro");

        assertThat(avisos()).isEmpty();
    }

    // --- FAVORITE ----------------------------------------------------------------------------

    @Test
    void favorito_que_es_un_barrio_del_recorrido_avisa_con_FAVORITE() {
        suspendida(10, "Centro", "Galería", "Terminal");
        favorito("Centro");

        assertThat(avisos()).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(UserNotification.ROUTE_SUSPENDED);
            assertThat(n.because()).isEqualTo(NotificationReason.FAVORITE);
            assertThat(n.suspensionId()).isEqualTo(110L);
            assertThat(n.routeId()).isEqualTo(10L);
            assertThat(n.routeName()).isEqualTo("Ruta 10");
            assertThat(n.startsAt()).isEqualTo(AHORA.minusSeconds(3600));
            assertThat(n.endsAt()).isEqualTo(FIN);
            assertThat(n.reason()).isEqualTo("Obras");
        });
    }

    @Test
    void la_coincidencia_ignora_tildes_y_mayusculas() {
        suspendida(10, "Centro", "Galería", "Terminal");
        favorito("GALERIA");

        assertThat(avisos()).singleElement().extracting(UserNotification::because).isEqualTo(NotificationReason.FAVORITE);
    }

    @Test
    void favorito_que_es_un_lugar_a_distancia_de_caminata_del_trazado_avisa_con_FAVORITE() {
        // "Barrio Oeste" no es un barrio de la ruta, pero es un alias del lugar Oeste, que esta sobre el trazado.
        suspendida(10, "Centro", "Terminal");
        favorito("Barrio Oeste");

        assertThat(avisos()).singleElement().extracting(UserNotification::because).isEqualTo(NotificationReason.FAVORITE);
    }

    @Test
    void favorito_que_es_un_lugar_lejos_del_trazado_no_avisa() {
        suspendida(10, "Centro", "Terminal");
        favorito("Lejos");

        assertThat(avisos()).isEmpty();
    }

    @Test
    void una_ruta_sin_trazado_solo_sirve_el_favorito_por_barrio() {
        rutas.add(ruta(10, "Ruta 10", RouteStatus.SUSPENDIDA, List.of(), "Centro"));
        efectivas.add(new RouteSuspension(110L, 10L, "Ruta 10", AHORA.minusSeconds(60), FIN, null, null));
        favorito("Barrio Oeste");

        assertThat(avisos()).isEmpty();
    }

    // --- FREQUENT ----------------------------------------------------------------------------

    @Test
    void tres_viajes_de_los_ultimos_seis_en_la_ruta_avisa_con_FREQUENT() {
        suspendida(10, "Centro");
        viajeEn(10);
        viajeEn(77);
        viajeEn(10);
        viajeEn(77);
        viajeEn(10);
        viajeEn(77);

        assertThat(avisos()).singleElement().extracting(UserNotification::because).isEqualTo(NotificationReason.FREQUENT);
        verify(rides).findRecentByUser(USUARIO, 6);
    }

    @Test
    void dos_viajes_en_la_ruta_no_alcanzan() {
        suspendida(10, "Centro");
        viajeEn(10);
        viajeEn(77);
        viajeEn(10);
        viajeEn(77);

        assertThat(avisos()).isEmpty();
    }

    @Test
    void muchos_viajes_de_otra_ruta_no_cuentan() {
        suspendida(10, "Centro");
        for (int i = 0; i < 6; i++) {
            viajeEn(77);
        }

        assertThat(avisos()).isEmpty();
    }

    // --- varios casos ------------------------------------------------------------------------

    @Test
    void si_cumple_los_dos_motivos_sale_un_solo_aviso_y_es_FAVORITE() {
        suspendida(10, "Centro", "Terminal");
        favorito("Terminal");
        viajeEn(10);
        viajeEn(10);
        viajeEn(10);

        assertThat(avisos()).singleElement().extracting(UserNotification::because).isEqualTo(NotificationReason.FAVORITE);
    }

    @Test
    void cada_ruta_suspendida_que_afecta_al_usuario_genera_su_aviso_en_el_orden_del_puerto() {
        suspendida(10, "Centro");
        suspendida(11, "Terminal");
        suspendida(12, "Otro");
        favorito("Terminal");
        viajeEn(10);
        viajeEn(10);
        viajeEn(10);

        assertThat(avisos()).extracting(UserNotification::routeId, UserNotification::because)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, NotificationReason.FREQUENT),
                        org.assertj.core.groups.Tuple.tuple(11L, NotificationReason.FAVORITE));
    }

    @Test
    void sin_favorito_no_se_consulta_el_catalogo_de_lugares() {
        suspendida(10, "Centro");
        viajeEn(10);

        avisos();

        verify(places, never()).findAll();
    }
}
