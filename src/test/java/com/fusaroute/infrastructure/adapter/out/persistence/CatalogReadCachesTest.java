package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Place;
import com.fusaroute.domain.model.RouteSuspension;
import com.fusaroute.domain.model.TrafficSector;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que acompana al catalogo en memoria (SCRUM-192): los lugares y los sectores se reutilizan durante su
 * tiempo de vida, y las escrituras de suspension invalidan el catalogo.
 */
class CatalogReadCachesTest {

    private static final long ELEVEN_MINUTES = 11 * 60 * 1000L;

    private static PlaceJpaEntity lugar(String nombre) {
        PlaceJpaEntity e = new PlaceJpaEntity();
        ReflectionTestUtils.setField(e, "id", 1L);
        ReflectionTestUtils.setField(e, "slug", "centro");
        ReflectionTestUtils.setField(e, "name", nombre);
        ReflectionTestUtils.setField(e, "lat", 4.34);
        ReflectionTestUtils.setField(e, "lng", -74.36);
        ReflectionTestUtils.setField(e, "aliases", List.of());
        return e;
    }

    private static TrafficSectorJpaEntity sector() {
        TrafficSectorJpaEntity e = new TrafficSectorJpaEntity();
        ReflectionTestUtils.setField(e, "id", 1L);
        ReflectionTestUtils.setField(e, "name", "Indio");
        ReflectionTestUtils.setField(e, "startTime", LocalTime.of(12, 0));
        ReflectionTestUtils.setField(e, "endTime", LocalTime.of(14, 0));
        ReflectionTestUtils.setField(e, "neighborhoods", Set.of("Indio"));
        return e;
    }

    @Test
    void los_sectores_se_leen_una_vez_y_se_reutilizan() {
        SpringDataTrafficSectorRepository repo = mock(SpringDataTrafficSectorRepository.class);
        when(repo.findAll()).thenReturn(List.of(sector()));
        TrafficSectorPersistenceAdapter adapter = new TrafficSectorPersistenceAdapter(repo);

        List<TrafficSector> primera = adapter.findAll();

        assertThat(adapter.findAll()).isSameAs(primera);
        assertThat(primera).singleElement().extracting(TrafficSector::name).isEqualTo("Indio");
        verify(repo, times(1)).findAll();
    }

    @Test
    void los_sectores_se_releen_al_vencer_el_tiempo_de_vida() {
        SpringDataTrafficSectorRepository repo = mock(SpringDataTrafficSectorRepository.class);
        when(repo.findAll()).thenReturn(List.of(sector()));
        TrafficSectorPersistenceAdapter adapter = new TrafficSectorPersistenceAdapter(repo);
        adapter.findAll();

        ReflectionTestUtils.setField(adapter, "cachedAt", System.currentTimeMillis() - ELEVEN_MINUTES);
        adapter.findAll();

        verify(repo, times(2)).findAll();
    }

    @Test
    void releer_los_lugares_sin_cambios_conserva_la_misma_lista() {
        SpringDataPlaceRepository repo = mock(SpringDataPlaceRepository.class);
        when(repo.findAll()).thenAnswer(inv -> List.of(lugar("Centro")));
        PlacePersistenceAdapter adapter = new PlacePersistenceAdapter(repo);
        List<Place> primera = adapter.findAll();

        ReflectionTestUtils.setField(adapter, "cachedAt", System.currentTimeMillis() - ELEVEN_MINUTES);

        assertThat(adapter.findAll()).isSameAs(primera);
        verify(repo, times(2)).findAll();
    }

    @Test
    void releer_los_lugares_con_un_cambio_entrega_la_lista_nueva() {
        SpringDataPlaceRepository repo = mock(SpringDataPlaceRepository.class);
        when(repo.findAll()).thenReturn(List.of(lugar("Centro")), List.of(lugar("Centro historico")));
        PlacePersistenceAdapter adapter = new PlacePersistenceAdapter(repo);
        List<Place> primera = adapter.findAll();

        ReflectionTestUtils.setField(adapter, "cachedAt", System.currentTimeMillis() - ELEVEN_MINUTES);

        assertThat(adapter.findAll()).isNotSameAs(primera)
                .singleElement().extracting(Place::name).isEqualTo("Centro historico");
    }

    @Test
    void suspender_y_levantar_invalidan_el_catalogo_en_memoria() {
        SpringDataRouteSuspensionRepository repo = mock(SpringDataRouteSuspensionRepository.class);
        CachedRouteCatalog catalog = mock(CachedRouteCatalog.class);
        Instant inicio = Instant.parse("2026-11-20T13:00:00Z");
        Instant fin = Instant.parse("2026-11-22T13:00:00Z");
        RouteSuspensionRow fila = mock(RouteSuspensionRow.class);
        when(fila.getId()).thenReturn(7L);
        when(fila.getRouteId()).thenReturn(10L);
        when(fila.getRouteName()).thenReturn("Oeste - Este");
        when(fila.getStartsAt()).thenReturn(inicio);
        when(fila.getEndsAt()).thenReturn(fin);
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            RouteSuspensionJpaEntity guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 7L);
            return guardada;
        });
        when(repo.findRowById(7L)).thenReturn(Optional.of(fila));
        RouteSuspensionPersistenceAdapter adapter = new RouteSuspensionPersistenceAdapter(repo, catalog);

        adapter.save(new RouteSuspension(null, 10L, "Oeste - Este", inicio, fin, "Obras", null), 1L);
        verify(catalog, times(1)).invalidateAfterCommit();

        adapter.markLifted(7L, inicio.plusSeconds(60));
        verify(catalog, times(2)).invalidateAfterCommit();

        // Consultar no invalida.
        adapter.findAll();
        adapter.findById(7L);
        verify(catalog, times(2)).invalidateAfterCommit();
    }
}
