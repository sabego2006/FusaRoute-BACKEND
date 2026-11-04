package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.FavoriteDestination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FavoriteDestinationPersistenceAdapter} con el repositorio de Spring Data
 * mockeado (RF-10, SCRUM-21).
 *
 * Verifica la traduccion entre el modelo de dominio y el de persistencia, que es lo
 * unico propio del adaptador. Que la tabla exista y que guardar reemplace de verdad se
 * prueba contra un Postgres real en {@link FavoriteDestinationPersistenceAdapterIT}.
 */
@ExtendWith(MockitoExtension.class)
class FavoriteDestinationPersistenceAdapterTest {

    private static final Instant MARCADO = Instant.parse("2026-11-04T09:15:00Z");

    @Mock
    private SpringDataFavoriteDestinationRepository repository;

    @Captor
    private ArgumentCaptor<FavoriteDestinationJpaEntity> entidadGuardada;

    private FavoriteDestinationPersistenceAdapter adapter() {
        return new FavoriteDestinationPersistenceAdapter(repository);
    }

    @Test
    void guardar_traduce_el_favorito_de_dominio_a_la_entidad() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        adapter().save(new FavoriteDestination(7L, "Maiz Amarillo", MARCADO));

        verify(repository).save(entidadGuardada.capture());
        FavoriteDestinationJpaEntity entidad = entidadGuardada.getValue();
        assertThat(entidad.getUserId()).isEqualTo(7L);
        assertThat(entidad.getDestination()).isEqualTo("Maiz Amarillo");
        assertThat(entidad.getCreatedAt()).isEqualTo(MARCADO);
    }

    @Test
    void guardar_devuelve_el_favorito_releido_de_la_entidad() {
        when(repository.save(any()))
                .thenReturn(new FavoriteDestinationJpaEntity(7L, "Centro", MARCADO));

        FavoriteDestination guardado = adapter().save(new FavoriteDestination(7L, "Centro", MARCADO));

        assertThat(guardado.userId()).isEqualTo(7L);
        assertThat(guardado.destination()).isEqualTo("Centro");
        assertThat(guardado.savedAt()).isEqualTo(MARCADO);
    }

    @Test
    void consultar_busca_por_el_id_del_usuario_que_es_la_llave_primaria() {
        when(repository.findById(7L))
                .thenReturn(Optional.of(new FavoriteDestinationJpaEntity(7L, "Centro", MARCADO)));

        assertThat(adapter().findByUser(7L)).hasValueSatisfying(favorito -> {
            assertThat(favorito.userId()).isEqualTo(7L);
            assertThat(favorito.destination()).isEqualTo("Centro");
        });

        verify(repository).findById(7L);
    }

    @Test
    void un_usuario_sin_favorito_devuelve_vacio() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(adapter().findByUser(99L)).isEmpty();
    }

    @Test
    void quitar_borra_por_el_id_del_usuario() {
        adapter().deleteByUser(7L);

        verify(repository).deleteById(7L);
    }
}
