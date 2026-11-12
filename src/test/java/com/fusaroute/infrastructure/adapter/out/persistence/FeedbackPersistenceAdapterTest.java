package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FeedbackPersistenceAdapter} con el repositorio de Spring Data mockeado
 * (RF-08 y RF-14, SCRUM-22 y SCRUM-23).
 *
 * Verifica la traduccion entre el modelo de dominio y el de persistencia en los dos
 * sentidos, que es lo unico propio del adaptador, y a que consulta delega cada metodo
 * del puerto. Que la tabla exista, que los CHECK esten puestos y que el orden
 * "pendientes primero" salga de verdad asi se prueba contra un Postgres real en
 * {@link FeedbackPersistenceAdapterIT}.
 *
 * Existe aparte del IT por la misma razon que el del destino favorito: el IT se salta
 * donde no hay Docker, y la traduccion de campos es justo lo que no debe quedar sin
 * verificar en esa maquina.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackPersistenceAdapterTest {

    private static final Instant ESCRITO = Instant.parse("2026-11-07T09:00:00Z");
    private static final Instant RESPONDIDO = Instant.parse("2026-11-07T15:00:00Z");

    @Mock
    private SpringDataFeedbackRepository repository;

    @Captor
    private ArgumentCaptor<FeedbackJpaEntity> entidadGuardada;

    private FeedbackPersistenceAdapter adapter() {
        return new FeedbackPersistenceAdapter(repository);
    }

    private static FeedbackJpaEntity filaPendiente() {
        return new FeedbackJpaEntity(5L, 7L, "Ana", "Comentario pendiente de Ana",
                FeedbackStatus.PENDING, null, null, ESCRITO, ESCRITO);
    }

    private static FeedbackJpaEntity filaRespondida() {
        return new FeedbackJpaEntity(6L, 7L, "Ana", "Comentario respondido de Ana",
                FeedbackStatus.ANSWERED, "Ya lo revisamos", RESPONDIDO, ESCRITO, RESPONDIDO);
    }

    @Test
    void la_pagina_del_feed_pide_el_orden_al_repositorio_con_el_pageable_y_traduce_las_filas() {
        when(repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(2, 10)))
                .thenReturn(List.of(filaRespondida(), filaPendiente()));

        List<Feedback> pagina = adapter().findAllPage(2, 10);

        verify(repository).findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(2, 10));
        assertThat(pagina).extracting(Feedback::getId).containsExactly(6L, 5L);
        assertThat(pagina.get(0).getStatus()).isEqualTo(FeedbackStatus.ANSWERED);
        assertThat(pagina.get(0).getReply()).isEqualTo("Ya lo revisamos");
    }

    @Test
    void el_total_del_feed_es_el_conteo_de_todas_las_filas() {
        when(repository.count()).thenReturn(42L);

        assertThat(adapter().countAll()).isEqualTo(42L);
    }

    @Test
    void guardar_traduce_el_comentario_de_dominio_a_la_entidad() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        adapter().save(Feedback.create(7L, "Ana", "La ruta deberia pasar mas seguido",
                Set.of(), ESCRITO));

        verify(repository).save(entidadGuardada.capture());
        FeedbackJpaEntity entidad = entidadGuardada.getValue();
        // id nulo: lo asigna la base. Es lo que hace que save() sirva para insertar.
        assertThat(entidad.getId()).isNull();
        assertThat(entidad.getUserId()).isEqualTo(7L);
        assertThat(entidad.getAuthorName()).isEqualTo("Ana");
        assertThat(entidad.getText()).isEqualTo("La ruta deberia pasar mas seguido");
        assertThat(entidad.getStatus()).isEqualTo(FeedbackStatus.PENDING);
        assertThat(entidad.getReply()).isNull();
        assertThat(entidad.getAnsweredAt()).isNull();
        assertThat(entidad.getCreatedAt()).isEqualTo(ESCRITO);
        assertThat(entidad.getUpdatedAt()).isEqualTo(ESCRITO);
    }

    @Test
    void guardar_un_comentario_respondido_lleva_la_respuesta_y_su_fecha_a_la_entidad() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        adapter().save(Feedback.create(7L, "Ana", "La ruta no pasa por mi barrio", Set.of(), ESCRITO)
                .withId(5L)
                .answeredWith("Ya agregamos el barrio", RESPONDIDO));

        verify(repository).save(entidadGuardada.capture());
        FeedbackJpaEntity entidad = entidadGuardada.getValue();
        // Con id, el mismo save() actualiza en vez de insertar: por eso editar y
        // responder no necesitan un metodo aparte en el puerto.
        assertThat(entidad.getId()).isEqualTo(5L);
        assertThat(entidad.getStatus()).isEqualTo(FeedbackStatus.ANSWERED);
        assertThat(entidad.getReply()).isEqualTo("Ya agregamos el barrio");
        assertThat(entidad.getAnsweredAt()).isEqualTo(RESPONDIDO);
        assertThat(entidad.getUpdatedAt()).isEqualTo(RESPONDIDO);
        // createdAt no se mueve al responder: el comentario sigue siendo el mismo.
        assertThat(entidad.getCreatedAt()).isEqualTo(ESCRITO);
    }

    @Test
    void guardar_devuelve_el_comentario_releido_de_la_entidad() {
        when(repository.save(any())).thenReturn(filaPendiente());

        Feedback guardado = adapter().save(
                Feedback.create(7L, "Ana", "Comentario cualquiera valido", Set.of(), ESCRITO));

        assertThat(guardado.getId()).isEqualTo(5L);
        assertThat(guardado.getText()).isEqualTo("Comentario pendiente de Ana");
        assertThat(guardado.isPending()).isTrue();
    }

    @Test
    void consultar_por_id_traduce_la_fila_respondida_completa() {
        when(repository.findById(6L)).thenReturn(Optional.of(filaRespondida()));

        assertThat(adapter().findById(6L)).hasValueSatisfying(comentario -> {
            assertThat(comentario.getId()).isEqualTo(6L);
            assertThat(comentario.getUserId()).isEqualTo(7L);
            assertThat(comentario.getAuthorName()).isEqualTo("Ana");
            assertThat(comentario.getStatus()).isEqualTo(FeedbackStatus.ANSWERED);
            assertThat(comentario.getReply()).isEqualTo("Ya lo revisamos");
            assertThat(comentario.getAnsweredAt()).isEqualTo(RESPONDIDO);
            assertThat(comentario.isPending()).isFalse();
        });
    }

    @Test
    void un_comentario_que_no_existe_devuelve_vacio() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThat(adapter().findById(404L)).isEmpty();
    }

    @Test
    void el_listado_del_usuario_delega_en_la_consulta_ordenada_por_fecha() {
        when(repository.findByUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(filaPendiente(), filaRespondida()));

        assertThat(adapter().findByUser(7L))
                .extracting(Feedback::getId)
                .containsExactly(5L, 6L);

        verify(repository).findByUserIdOrderByCreatedAtDesc(7L);
    }

    @Test
    void un_usuario_sin_comentarios_devuelve_lista_vacia() {
        when(repository.findByUserIdOrderByCreatedAtDesc(99L)).thenReturn(List.of());

        assertThat(adapter().findByUser(99L)).isEmpty();
    }

    @Test
    void borrar_delega_en_delete_by_id() {
        adapter().delete(5L);

        verify(repository).deleteById(5L);
    }

    /**
     * El conteo de pendientes es la metrica 4 del panel de RF-13, asi que importa que
     * cuente el estado PENDING y no otro: un error aqui daria un numero plausible pero
     * equivocado en el panel, que es peor que un error visible.
     */
    @Test
    void el_conteo_de_pendientes_pregunta_por_el_estado_pendiente() {
        when(repository.countByStatus(FeedbackStatus.PENDING)).thenReturn(4L);

        assertThat(adapter().countPending()).isEqualTo(4L);

        verify(repository).countByStatus(FeedbackStatus.PENDING);
    }

    @Test
    void el_conteo_de_respuestas_nuevas_filtra_por_usuario_estado_y_fecha() {
        Instant corte = Instant.parse("2026-11-06T00:00:00Z");
        when(repository.countByUserIdAndStatusAndAnsweredAtAfter(7L, FeedbackStatus.ANSWERED, corte))
                .thenReturn(2L);

        assertThat(adapter().countAnsweredSince(7L, corte)).isEqualTo(2L);

        verify(repository).countByUserIdAndStatusAndAnsweredAtAfter(7L, FeedbackStatus.ANSWERED, corte);
    }

    @Test
    void el_listado_del_administrador_sin_filtro_usa_la_consulta_de_pendientes_primero() {
        when(repository.findAllPendingFirst()).thenReturn(List.of(filaPendiente(), filaRespondida()));

        assertThat(adapter().findAllPendingFirst())
                .extracting(Feedback::getId)
                .containsExactly(5L, 6L);

        verify(repository).findAllPendingFirst();
    }

    @Test
    void el_listado_del_administrador_filtrado_usa_la_consulta_por_estado() {
        when(repository.findByStatusOrderByCreatedAtDesc(FeedbackStatus.ANSWERED))
                .thenReturn(List.of(filaRespondida()));

        assertThat(adapter().findAllByStatus(FeedbackStatus.ANSWERED))
                .extracting(Feedback::getId)
                .containsExactly(6L);

        verify(repository).findByStatusOrderByCreatedAtDesc(FeedbackStatus.ANSWERED);
    }
}
