package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.FeedbackNotFoundException;
import com.fusaroute.domain.exception.InvalidFeedbackReplyException;
import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;
import com.fusaroute.domain.port.out.FeedbackRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Casos de uso del administrador sobre los comentarios (RF-14, SCRUM-23), con el
 * repositorio mockeado.
 */
@ExtendWith(MockitoExtension.class)
class AdminFeedbackServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-07T15:00:00Z");

    @Mock
    private FeedbackRepositoryPort repository;

    private AdminFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new AdminFeedbackService(repository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Feedback pendiente(Long id) {
        return new Feedback(id, 7L, "Ana", "Comentario pendiente de Ana",
                FeedbackStatus.PENDING, null, null, AHORA, AHORA);
    }

    private static Feedback respondido(Long id, String respuesta, Instant cuando) {
        return new Feedback(id, 7L, "Ana", "Comentario respondido de Ana",
                FeedbackStatus.ANSWERED, respuesta, cuando, AHORA, cuando);
    }

    // --- SCRUM-98: listado ------------------------------------------------------

    @Test
    void el_listado_sin_filtro_usa_la_consulta_de_pendientes_primero() {
        when(repository.findAllPendingFirst()).thenReturn(List.of(pendiente(1L)));

        assertThat(service.allFeedbackPendingFirst()).hasSize(1);

        // El orden lo decide la consulta, no el servicio: ordenar aqui obligaria a
        // traer la tabla completa para reordenarla en memoria.
        verify(repository).findAllPendingFirst();
    }

    @Test
    void el_listado_filtrado_usa_la_consulta_por_estado() {
        when(repository.findAllByStatus(FeedbackStatus.PENDING))
                .thenReturn(List.of(pendiente(1L), pendiente(2L)));

        assertThat(service.allFeedbackWithStatus(FeedbackStatus.PENDING)).hasSize(2);

        verify(repository).findAllByStatus(FeedbackStatus.PENDING);
        verify(repository, never()).findAllPendingFirst();
    }

    @Test
    void sin_comentarios_el_listado_es_vacio_y_no_falla() {
        when(repository.findAllPendingFirst()).thenReturn(List.of());

        assertThat(service.allFeedbackPendingFirst()).isEmpty();
    }

    // --- SCRUM-99: responder ----------------------------------------------------

    @Test
    void responder_pasa_el_comentario_a_respondido_y_fija_la_fecha() {
        when(repository.findById(5L)).thenReturn(Optional.of(pendiente(5L)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reply(5L, "Ya agregamos el barrio al recorrido");

        ArgumentCaptor<Feedback> guardado = ArgumentCaptor.forClass(Feedback.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().getStatus()).isEqualTo(FeedbackStatus.ANSWERED);
        assertThat(guardado.getValue().getReply()).isEqualTo("Ya agregamos el barrio al recorrido");
        assertThat(guardado.getValue().getAnsweredAt()).isEqualTo(AHORA);
        // El texto del usuario no se toca al responder.
        assertThat(guardado.getValue().getText()).isEqualTo("Comentario pendiente de Ana");
    }

    @Test
    void responder_un_comentario_que_no_existe_es_un_no_encontrado() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(404L, "Respuesta cualquiera"))
                .isInstanceOf(FeedbackNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void una_respuesta_vacia_no_llega_a_la_base() {
        when(repository.findById(5L)).thenReturn(Optional.of(pendiente(5L)));

        assertThatThrownBy(() -> service.reply(5L, "   "))
                .isInstanceOf(InvalidFeedbackReplyException.class);

        verify(repository, never()).save(any());
    }

    // --- SCRUM-100: corregir la respuesta ---------------------------------------

    /**
     * El criterio de RF-14: "una respuesta por comentario; si el administrador
     * necesita corregir su respuesta, la edita, no crea una segunda". Responder un
     * comentario YA respondido no falla y no duplica: reescribe.
     */
    @Test
    void responder_de_nuevo_reemplaza_la_respuesta_en_vez_de_fallar() {
        Instant primera = AHORA.minusSeconds(7200);
        when(repository.findById(5L))
                .thenReturn(Optional.of(respondido(5L, "Lo revisamos manana", primera)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reply(5L, "Ya quedo agregado el barrio");

        ArgumentCaptor<Feedback> guardado = ArgumentCaptor.forClass(Feedback.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().getReply()).isEqualTo("Ya quedo agregado el barrio");
        assertThat(guardado.getValue().getStatus()).isEqualTo(FeedbackStatus.ANSWERED);
        // answered_at se mueve a la correccion, y eso es lo que hace que el contador
        // de respuestas nuevas (SCRUM-101) vuelva a avisarle al usuario del cambio.
        assertThat(guardado.getValue().getAnsweredAt()).isEqualTo(AHORA);
    }

    /**
     * Un solo save, no dos: responder de nuevo no borra la respuesta vieja para
     * insertar una nueva. Si este test se pone rojo, alguien convirtio la respuesta
     * en un hilo, que es justo lo que el criterio descarta.
     */
    @Test
    void corregir_la_respuesta_escribe_una_sola_vez() {
        when(repository.findById(5L))
                .thenReturn(Optional.of(respondido(5L, "Primera", AHORA.minusSeconds(60))));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reply(5L, "Segunda y definitiva");

        verify(repository).save(any());
        verify(repository, never()).delete(anyLong());
    }
}
