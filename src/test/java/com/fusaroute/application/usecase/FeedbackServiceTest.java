package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.FeedbackAlreadyAnsweredException;
import com.fusaroute.domain.exception.FeedbackForbiddenWordException;
import com.fusaroute.domain.exception.FeedbackLengthException;
import com.fusaroute.domain.exception.FeedbackNotFoundException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.out.FeedbackRepositoryPort;
import com.fusaroute.domain.port.out.ForbiddenWordsPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Casos de uso de la caja de comentarios (RF-08, SCRUM-22), con los puertos
 * mockeados y sin levantar Spring.
 *
 * Lo que se prueba aqui es la ORQUESTACION, no las reglas: que se lea el nombre del
 * autor del perfil y no del cuerpo, que se compruebe autoria antes que estado, que
 * borrar no llame al repositorio cuando el comentario ya fue respondido, y que el
 * contador compare contra el ingreso anterior. Las reglas del texto tienen sus
 * propios tests en {@code FeedbackTest}.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    private static final Long AUTOR = 7L;
    private static final Long OTRO = 99L;
    private static final Instant AHORA = Instant.parse("2026-11-07T10:00:00Z");

    @Mock
    private FeedbackRepositoryPort repository;

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private ForbiddenWordsPort forbiddenWords;

    private FeedbackService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackService(repository, userRepository, forbiddenWords,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static User ana() {
        return new User(AUTOR, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0);
    }

    private static Feedback pendienteDeAna() {
        return new Feedback(5L, AUTOR, "Ana", "Comentario pendiente de Ana",
                FeedbackStatus.PENDING, null, null, AHORA, AHORA);
    }

    private static Feedback respondidoDeAna() {
        return new Feedback(5L, AUTOR, "Ana", "Comentario respondido de Ana",
                FeedbackStatus.ANSWERED, "Ya lo revisamos", AHORA, AHORA, AHORA);
    }

    // --- SCRUM-92: dejar un comentario ------------------------------------------

    @Test
    void guarda_el_comentario_con_el_nombre_leido_del_perfil() {
        when(userRepository.findById(AUTOR)).thenReturn(Optional.of(ana()));
        when(forbiddenWords.words()).thenReturn(Set.of());
        when(repository.save(any())).thenAnswer(inv -> ((Feedback) inv.getArgument(0)).withId(5L));

        service.submit(AUTOR, "La ruta deberia pasar mas seguido");

        ArgumentCaptor<Feedback> guardado = ArgumentCaptor.forClass(Feedback.class);
        verify(repository).save(guardado.capture());
        // El nombre sale del perfil, no de la peticion: es lo que impide firmar un
        // comentario con el nombre de otro.
        assertThat(guardado.getValue().getAuthorName()).isEqualTo("Ana");
        assertThat(guardado.getValue().getUserId()).isEqualTo(AUTOR);
        assertThat(guardado.getValue().getStatus()).isEqualTo(FeedbackStatus.PENDING);
        assertThat(guardado.getValue().getCreatedAt()).isEqualTo(AHORA);
    }

    @Test
    void un_comentario_invalido_no_llega_a_la_base() {
        when(userRepository.findById(AUTOR)).thenReturn(Optional.of(ana()));

        assertThatThrownBy(() -> service.submit(AUTOR, "corto"))
                .isInstanceOf(FeedbackLengthException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void el_banco_de_palabras_se_consulta_por_el_puerto_y_se_aplica() {
        when(userRepository.findById(AUTOR)).thenReturn(Optional.of(ana()));
        when(forbiddenWords.words()).thenReturn(Set.of("idiota"));

        assertThatThrownBy(() -> service.submit(AUTOR, "El conductor es un idiota"))
                .isInstanceOf(FeedbackForbiddenWordException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void comentar_con_un_usuario_que_no_existe_falla_antes_de_validar_el_texto() {
        when(userRepository.findById(AUTOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(AUTOR, "Comentario perfectamente valido"))
                .isInstanceOf(UserNotFoundException.class);

        verify(repository, never()).save(any());
    }

    // --- Mis comentarios --------------------------------------------------------

    @Test
    void devuelve_los_comentarios_del_usuario_tal_como_los_ordena_el_repositorio() {
        when(repository.findByUser(AUTOR)).thenReturn(List.of(pendienteDeAna()));

        assertThat(service.myFeedback(AUTOR)).hasSize(1);
    }

    @Test
    void sin_comentarios_devuelve_lista_vacia_y_no_falla() {
        when(repository.findByUser(AUTOR)).thenReturn(List.of());

        assertThat(service.myFeedback(AUTOR)).isEmpty();
    }

    // --- SCRUM-94: editar -------------------------------------------------------

    @Test
    void editar_un_comentario_propio_pendiente_lo_guarda_con_el_texto_nuevo() {
        when(repository.findById(5L)).thenReturn(Optional.of(pendienteDeAna()));
        when(forbiddenWords.words()).thenReturn(Set.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.edit(AUTOR, 5L, "Comentario corregido por Ana");

        ArgumentCaptor<Feedback> guardado = ArgumentCaptor.forClass(Feedback.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().getText()).isEqualTo("Comentario corregido por Ana");
        assertThat(guardado.getValue().getUpdatedAt()).isEqualTo(AHORA);
    }

    @Test
    void editar_un_comentario_que_no_existe_es_un_no_encontrado() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(AUTOR, 404L, "Comentario cualquiera aqui"))
                .isInstanceOf(FeedbackNotFoundException.class);
    }

    /**
     * El comentario ajeno responde "no encontrado" y no "sin permiso": ver el javadoc
     * de {@code FeedbackNotFoundException}. Un 403 confirmaria que ese id existe.
     */
    @Test
    void editar_el_comentario_de_otro_usuario_es_un_no_encontrado() {
        when(repository.findById(5L)).thenReturn(Optional.of(pendienteDeAna()));

        assertThatThrownBy(() -> service.edit(OTRO, 5L, "Comentario de alguien mas"))
                .isInstanceOf(FeedbackNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void editar_un_comentario_ya_respondido_choca_con_el_estado() {
        when(repository.findById(5L)).thenReturn(Optional.of(respondidoDeAna()));

        assertThatThrownBy(() -> service.edit(AUTOR, 5L, "Intento de cambiar el texto"))
                .isInstanceOf(FeedbackAlreadyAnsweredException.class);

        verify(repository, never()).save(any());
    }

    // --- SCRUM-95: borrar -------------------------------------------------------

    @Test
    void borrar_un_comentario_propio_pendiente_lo_borra() {
        when(repository.findById(5L)).thenReturn(Optional.of(pendienteDeAna()));

        service.delete(AUTOR, 5L);

        verify(repository).delete(5L);
    }

    @Test
    void borrar_el_comentario_de_otro_usuario_es_un_no_encontrado() {
        when(repository.findById(5L)).thenReturn(Optional.of(pendienteDeAna()));

        assertThatThrownBy(() -> service.delete(OTRO, 5L))
                .isInstanceOf(FeedbackNotFoundException.class);

        verify(repository, never()).delete(anyLong());
    }

    @Test
    void borrar_un_comentario_ya_respondido_choca_con_el_estado() {
        when(repository.findById(5L)).thenReturn(Optional.of(respondidoDeAna()));

        assertThatThrownBy(() -> service.delete(AUTOR, 5L))
                .isInstanceOf(FeedbackAlreadyAnsweredException.class);

        verify(repository, never()).delete(anyLong());
    }

    @Test
    void borrar_un_comentario_que_no_existe_es_un_no_encontrado() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(AUTOR, 404L))
                .isInstanceOf(FeedbackNotFoundException.class);
    }

    // --- Metrica 4 de RF-13 -----------------------------------------------------

    @Test
    void el_conteo_de_pendientes_sale_del_repositorio() {
        when(repository.countPending()).thenReturn(4L);

        assertThat(service.countPending()).isEqualTo(4L);
    }

    // --- SCRUM-101: respuestas nuevas desde el ingreso anterior -----------------

    /**
     * El contador compara contra el ingreso ANTERIOR, no contra el ultimo. Si
     * alguien cambia el servicio para usar {@code getLastLoginAt()}, este test se
     * pone rojo: es el defecto que haria que el contador diera cero siempre, porque
     * el login ya movio esa columna a "ahora" cuando el frontend pregunta.
     */
    @Test
    void cuenta_las_respuestas_desde_el_ingreso_anterior_y_no_desde_el_ultimo() {
        Instant anterior = Instant.parse("2026-11-05T08:00:00Z");
        Instant ultimo = Instant.parse("2026-11-07T09:59:00Z");
        User conDosIngresos = new User(AUTOR, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, false, null, null, ultimo, anterior);
        when(userRepository.findById(AUTOR)).thenReturn(Optional.of(conDosIngresos));
        when(repository.countAnsweredSince(AUTOR, anterior)).thenReturn(2L);

        assertThat(service.unreadReplies(AUTOR)).isEqualTo(2L);

        verify(repository).countAnsweredSince(AUTOR, anterior);
    }

    @Test
    void en_el_primer_ingreso_el_contador_es_cero_y_no_consulta_comentarios() {
        User recienLlegada = new User(AUTOR, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, false, null, null, AHORA, null);
        when(userRepository.findById(AUTOR)).thenReturn(Optional.of(recienLlegada));

        assertThat(service.unreadReplies(AUTOR)).isZero();

        verify(repository, never()).countAnsweredSince(anyLong(), any());
    }

    @Test
    void el_contador_de_un_usuario_que_no_existe_falla_con_usuario_no_encontrado() {
        when(userRepository.findById(AUTOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unreadReplies(AUTOR))
                .isInstanceOf(UserNotFoundException.class);
    }
}
