package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las dos banderas que el cliente recibe resueltas: {@code mine} y {@code editable}.
 * Sin Spring: es una funcion pura de (comentario, quien mira).
 */
class CommentResponseTest {

    private static final Long AUTOR = 7L;
    private static final Long OTRO = 99L;
    private static final Instant AHORA = Instant.parse("2026-11-07T10:00:00Z");

    private static Feedback pendiente() {
        return new Feedback(5L, AUTOR, "Ana", "Comentario pendiente de Ana",
                FeedbackStatus.PENDING, null, null, AHORA, AHORA);
    }

    private static Feedback respondido() {
        return new Feedback(6L, AUTOR, "Ana", "Comentario respondido de Ana",
                FeedbackStatus.ANSWERED, "Ya lo revisamos", AHORA, AHORA, AHORA);
    }

    @Test
    void mine_es_true_solo_para_el_autor() {
        assertThat(CommentResponse.from(pendiente(), AUTOR).mine()).isTrue();
        assertThat(CommentResponse.from(pendiente(), OTRO).mine()).isFalse();
        assertThat(CommentResponse.from(respondido(), AUTOR).mine()).isTrue();
        assertThat(CommentResponse.from(respondido(), OTRO).mine()).isFalse();
    }

    @Test
    void el_autor_edita_su_comentario_mientras_este_pendiente() {
        assertThat(CommentResponse.from(pendiente(), AUTOR).editable()).isTrue();
    }

    @Test
    void el_comentario_pendiente_de_otro_usuario_no_es_editable() {
        // Antes de mine, editable era solo "esta pendiente": en el feed compartido eso
        // le habria ofrecido editar el comentario de otro.
        CommentResponse vista = CommentResponse.from(pendiente(), OTRO);

        assertThat(vista.status()).isEqualTo("PENDING");
        assertThat(vista.editable()).isFalse();
    }

    @Test
    void el_comentario_propio_ya_respondido_no_es_editable() {
        CommentResponse vista = CommentResponse.from(respondido(), AUTOR);

        assertThat(vista.mine()).isTrue();
        assertThat(vista.editable()).isFalse();
    }

    @Test
    void la_respuesta_trae_la_fecha_de_respuesta_y_ningun_nombre_de_administrador() {
        CommentResponse vista = CommentResponse.from(respondido(), OTRO);

        assertThat(vista.reply()).isEqualTo("Ya lo revisamos");
        assertThat(vista.answeredAt()).isEqualTo(AHORA);
        assertThat(Arrays.stream(CommentResponse.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("userId", "replyAuthor", "adminName");
    }
}
