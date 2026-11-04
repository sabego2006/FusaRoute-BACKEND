package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.SearchRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SearchHistoryPersistenceAdapter} con el repositorio de Spring Data mockeado
 * (RF-07, SCRUM-20).
 *
 * Lo que se verifica aqui es la traduccion entre el modelo de dominio y el de
 * persistencia, que es lo unico propio del adaptador: que el {@code SearchRecord} que
 * entra se convierta en la entidad correcta, que lo que sale de la base vuelva a ser
 * dominio, y que el {@code Pageable} acote la lectura al tope pedido.
 *
 * Que la tabla exista, que la V17 aplique y que el recorte deje seis filas se prueba
 * contra un Postgres real en {@link SearchHistoryPersistenceAdapterIT}; con un mock
 * eso no se puede afirmar.
 */
@ExtendWith(MockitoExtension.class)
class SearchHistoryPersistenceAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T09:15:00Z");

    @Mock
    private SpringDataSearchHistoryRepository repository;

    @Captor
    private ArgumentCaptor<SearchHistoryJpaEntity> entidadGuardada;

    @Captor
    private ArgumentCaptor<Pageable> paginaPedida;

    private SearchHistoryPersistenceAdapter adapter() {
        return new SearchHistoryPersistenceAdapter(repository);
    }

    private static SearchHistoryJpaEntity fila(Long id, String origin, String destination, Instant createdAt) {
        SearchHistoryJpaEntity entity = new SearchHistoryJpaEntity(7L, origin, destination, createdAt);
        // El id lo asigna la base al insertar; en un test se fija por reflexion
        // porque la entidad no expone un setter, y no debe exponerlo solo para esto.
        try {
            var campo = SearchHistoryJpaEntity.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo fijar el id de prueba", e);
        }
        return entity;
    }

    @Test
    void guardar_traduce_el_registro_de_dominio_a_la_entidad() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        adapter().save(SearchRecord.of(7L, "Maiz Amarillo", "San Antonio", AHORA));

        verify(repository).save(entidadGuardada.capture());
        SearchHistoryJpaEntity entidad = entidadGuardada.getValue();
        assertThat(entidad.getUserId()).isEqualTo(7L);
        assertThat(entidad.getOrigin()).isEqualTo("Maiz Amarillo");
        assertThat(entidad.getDestination()).isEqualTo("San Antonio");
        assertThat(entidad.getCreatedAt()).isEqualTo(AHORA);
    }

    @Test
    void guardar_devuelve_el_registro_con_el_id_que_asigno_la_base() {
        when(repository.save(any())).thenReturn(fila(42L, "Maiz Amarillo", "San Antonio", AHORA));

        SearchRecord guardado = adapter().save(SearchRecord.of(7L, "Maiz Amarillo", "San Antonio", AHORA));

        assertThat(guardado.id()).isEqualTo(42L);
        assertThat(guardado.userId()).isEqualTo(7L);
        assertThat(guardado.origin()).isEqualTo("Maiz Amarillo");
        assertThat(guardado.destination()).isEqualTo("San Antonio");
        assertThat(guardado.searchedAt()).isEqualTo(AHORA);
    }

    @Test
    void leer_el_historial_pide_solo_la_primera_pagina_del_tamano_del_tope() {
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(List.of());

        adapter().findRecentByUser(7L, SearchRecord.MAX_PER_USER);

        verify(repository).findByUserIdOrderByCreatedAtDesc(eq(7L), paginaPedida.capture());
        Pageable pagina = paginaPedida.getValue();
        assertThat(pagina.getPageNumber()).isZero();
        assertThat(pagina.getPageSize()).isEqualTo(SearchRecord.MAX_PER_USER);
    }

    @Test
    void leer_el_historial_traduce_cada_fila_a_dominio_y_respeta_el_orden_de_la_consulta() {
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(List.of(
                fila(2L, "Centro", "Terminal", AHORA),
                fila(1L, "Maiz Amarillo", "San Antonio", AHORA.minusSeconds(60))));

        List<SearchRecord> historial = adapter().findRecentByUser(7L, SearchRecord.MAX_PER_USER);

        // El adaptador no reordena: el orden lo pone la consulta, que lee en el orden
        // del indice. Si aqui apareciera invertido, el adaptador estaria haciendo algo
        // que no le toca.
        assertThat(historial).extracting(SearchRecord::id).containsExactly(2L, 1L);
        assertThat(historial).extracting(SearchRecord::origin).containsExactly("Centro", "Maiz Amarillo");
        assertThat(historial).extracting(SearchRecord::destination).containsExactly("Terminal", "San Antonio");
    }

    @Test
    void un_usuario_sin_busquedas_devuelve_historial_vacio_y_no_null() {
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(99L), any())).thenReturn(List.of());

        assertThat(adapter().findRecentByUser(99L, SearchRecord.MAX_PER_USER)).isEmpty();
    }

    @Test
    void recortar_delega_en_la_consulta_y_devuelve_cuantas_filas_borro() {
        when(repository.deleteOlderBeyond(7L, SearchRecord.MAX_PER_USER)).thenReturn(1);

        assertThat(adapter().deleteOlderBeyond(7L, SearchRecord.MAX_PER_USER)).isEqualTo(1);

        verify(repository).deleteOlderBeyond(7L, SearchRecord.MAX_PER_USER);
    }
}
