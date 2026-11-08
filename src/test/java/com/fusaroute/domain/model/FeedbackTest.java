package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FeedbackAlreadyAnsweredException;
import com.fusaroute.domain.exception.FeedbackContainsUrlException;
import com.fusaroute.domain.exception.FeedbackForbiddenWordException;
import com.fusaroute.domain.exception.FeedbackLengthException;
import com.fusaroute.domain.exception.FeedbackNotFoundException;
import com.fusaroute.domain.exception.InvalidFeedbackReplyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas del comentario (RF-08 y RF-14, SCRUM-91, 94, 95, 99, 100).
 *
 * Cada criterio de aceptacion de las dos historias esta convertido en un caso de
 * aqui. Son tests de dominio puro: sin Spring, sin base de datos y sin mocks, porque
 * {@link Feedback} no depende de nada externo salvo el banco de palabras, que entra
 * como un {@code Set} por parametro.
 */
class FeedbackTest {

    private static final Long AUTOR = 7L;
    private static final Instant AHORA = Instant.parse("2026-11-07T10:00:00Z");
    private static final Set<String> SIN_BANCO = Set.of();

    private static Feedback nuevo(String texto) {
        return Feedback.create(AUTOR, "Ana", texto, SIN_BANCO, AHORA);
    }

    // --- SCRUM-91: longitud entre 10 y 100 caracteres ---------------------------

    /**
     * Las cuatro fronteras del criterio: 9 y 101 se rechazan, 10 y 100 se aceptan.
     * Probar solo "corto" y "largo" dejaria pasar un {@code <} escrito como
     * {@code <=}, que es justo el error que se comete al traducir "entre 10 y 100".
     */
    @Test
    void nueve_caracteres_se_rechaza_y_diez_se_acepta() {
        assertThatThrownBy(() -> nuevo("a".repeat(9)))
                .isInstanceOf(FeedbackLengthException.class)
                .hasMessageContaining("entre 10 y 100");

        assertThat(nuevo("a".repeat(10)).getText()).hasSize(10);
    }

    @Test
    void cien_caracteres_se_acepta_y_ciento_uno_se_rechaza() {
        assertThat(nuevo("a".repeat(100)).getText()).hasSize(100);

        assertThatThrownBy(() -> nuevo("a".repeat(101)))
                .isInstanceOf(FeedbackLengthException.class);
    }

    /**
     * La longitud se mide sobre el texto YA RECORTADO, como dice el criterio. Un
     * comentario de 8 letras rodeado de espacios no cumple el minimo por tener 12
     * caracteres.
     */
    @Test
    void la_longitud_se_mide_sobre_el_texto_recortado() {
        assertThatThrownBy(() -> nuevo("  hola ok  "))
                .isInstanceOf(FeedbackLengthException.class);

        assertThat(nuevo("   Buen servicio en la ruta   ").getText())
                .isEqualTo("Buen servicio en la ruta");
    }

    @Test
    void texto_nulo_o_en_blanco_falla_por_longitud_y_no_con_un_error_tecnico() {
        assertThatThrownBy(() -> nuevo(null)).isInstanceOf(FeedbackLengthException.class);
        assertThatThrownBy(() -> nuevo("          ")).isInstanceOf(FeedbackLengthException.class);
    }

    // --- SCRUM-91: sin URLs -----------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "Miren esto http://spam.example para mas informacion",
            "Mejor entren a HTTPS://SPAM.EXAMPLE y comparen",
            "Visiten www.spamdelaruta.algo y cuentenme",
            "La mejor app de rutas es rutasfalsas.com sin discusion",
            "Entren a promos.co que hay descuentos en pasajes"})
    void rechaza_el_comentario_que_trae_un_enlace(String conEnlace) {
        assertThatThrownBy(() -> nuevo(conEnlace))
                .isInstanceOf(FeedbackContainsUrlException.class)
                .hasMessageContaining("enlaces");
    }

    /**
     * El falso positivo que la lista cerrada de dominios existe para evitar: a quien
     * escribe un comentario se le va el espacio despues del punto, y eso NO es un
     * enlace. Si este test se pone rojo, alguien cambio el patron por uno generico y
     * la caja de comentarios empezo a rechazar comentarios legitimos.
     */
    @Test
    void un_punto_sin_espacio_en_prosa_normal_no_es_un_enlace() {
        assertThat(nuevo("Muy buen servicio.Gracias por la app").getText())
                .contains("servicio.Gracias");
    }

    /**
     * Fija la decision de dejar {@code .es} y {@code .me} FUERA de la lista de
     * dominios. Son dominios reales, pero en espanol "Es" y "Me" empiezan frases, y
     * con ellos dentro estos dos comentarios perfectamente normales se rechazarian.
     * Si alguien los agrega buscando cerrar huecos, este test se pone rojo y explica
     * por que no.
     */
    @Test
    void es_y_me_no_cuentan_como_dominio_porque_son_palabras_del_espanol() {
        assertThat(nuevo("El servicio.Es muy bueno de verdad").getText()).isNotEmpty();
        assertThat(nuevo("La ruta nueva.Me gusta bastante").getText()).isNotEmpty();
    }

    @Test
    void un_punto_final_no_es_un_enlace() {
        assertThat(nuevo("La ruta pasa muy seguido.").getText()).endsWith(".");
    }

    // --- SCRUM-91: banco de palabras prohibidas ---------------------------------

    @Test
    void rechaza_la_palabra_prohibida_tal_como_esta_en_el_banco() {
        assertThatThrownBy(() -> Feedback.create(
                AUTOR, "Ana", "El conductor es un idiota siempre", Set.of("idiota"), AHORA))
                .isInstanceOf(FeedbackForbiddenWordException.class);
    }

    /**
     * El banco se compara normalizado: una sola entrada cubre mayusculas y tildes.
     * Es lo que permite que el archivo de palabras tenga una linea por palabra y no
     * una por variante ortografica.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "El conductor es un IDIOTA de verdad",
            "El conductor es un Idiota de verdad",
            "El conductor es un idióta de verdad",
            "El conductor es un ÍDIOTA de verdad"})
    void rechaza_la_palabra_prohibida_en_mayusculas_y_con_tilde(String texto) {
        assertThatThrownBy(() -> Feedback.create(AUTOR, "Ana", texto, Set.of("idiota"), AHORA))
                .isInstanceOf(FeedbackForbiddenWordException.class);
    }

    @Test
    void la_puntuacion_pegada_no_esconde_la_palabra_prohibida() {
        assertThatThrownBy(() -> Feedback.create(
                AUTOR, "Ana", "Que servicio tan malo, idiota!", Set.of("idiota"), AHORA))
                .isInstanceOf(FeedbackForbiddenWordException.class);
    }

    /**
     * Palabra completa y no subcadena: el banco no debe tumbar palabras legitimas que
     * contengan una prohibida. El ejemplo es real —"sumapaz" contiene "paz"— y es el
     * tipo de falso positivo que haria inservible la caja de comentarios.
     */
    @Test
    void no_rechaza_una_palabra_legitima_que_contiene_a_la_prohibida() {
        assertThat(Feedback.create(AUTOR, "Ana", "La ruta al Sumapaz es muy buena",
                Set.of("paz"), AHORA).getText()).contains("Sumapaz");
    }

    @Test
    void un_banco_vacio_deja_pasar_cualquier_palabra() {
        assertThat(Feedback.create(AUTOR, "Ana", "Comentario cualquiera aqui",
                Set.of(), AHORA).getText()).isNotEmpty();
        assertThat(Feedback.create(AUTOR, "Ana", "Comentario cualquiera aqui",
                null, AHORA).getText()).isNotEmpty();
    }

    // --- SCRUM-92: estado inicial del comentario --------------------------------

    @Test
    void el_comentario_nuevo_nace_pendiente_sin_respuesta_y_con_el_nombre_del_autor() {
        Feedback feedback = nuevo("Me sirve mucho esta aplicacion");

        assertThat(feedback.getId()).isNull();
        assertThat(feedback.getUserId()).isEqualTo(AUTOR);
        assertThat(feedback.getAuthorName()).isEqualTo("Ana");
        assertThat(feedback.getStatus()).isEqualTo(FeedbackStatus.PENDING);
        assertThat(feedback.isPending()).isTrue();
        assertThat(feedback.getReply()).isNull();
        assertThat(feedback.getAnsweredAt()).isNull();
        assertThat(feedback.getCreatedAt()).isEqualTo(AHORA);
        assertThat(feedback.getUpdatedAt()).isEqualTo(AHORA);
    }

    // --- SCRUM-94 y SCRUM-95: solo el autor, y solo mientras este pendiente -----

    @Test
    void otro_usuario_no_es_el_autor_y_recibe_un_no_encontrado() {
        Feedback feedback = nuevo("Comentario de Ana sobre la ruta");

        assertThatThrownBy(() -> feedback.requireAuthoredBy(99L))
                .isInstanceOf(FeedbackNotFoundException.class);
    }

    @Test
    void el_autor_si_pasa_la_comprobacion_de_autoria() {
        nuevo("Comentario de Ana sobre la ruta").requireAuthoredBy(AUTOR);
    }

    @Test
    void editar_un_comentario_pendiente_cambia_el_texto_y_mueve_la_fecha_de_cambio() {
        Instant despues = AHORA.plusSeconds(600);
        Feedback editado = nuevo("Texto original del comentario")
                .editedTo("Texto corregido del comentario", SIN_BANCO, despues);

        assertThat(editado.getText()).isEqualTo("Texto corregido del comentario");
        assertThat(editado.getUpdatedAt()).isEqualTo(despues);
        // createdAt no se mueve: el comentario sigue siendo el mismo, solo cambio su
        // contenido. Moverlo lo reordenaria en el listado del administrador.
        assertThat(editado.getCreatedAt()).isEqualTo(AHORA);
        assertThat(editado.getStatus()).isEqualTo(FeedbackStatus.PENDING);
    }

    @Test
    void al_editar_se_vuelve_a_validar_el_texto() {
        Feedback pendiente = nuevo("Texto original del comentario");

        assertThatThrownBy(() -> pendiente.editedTo("corto", SIN_BANCO, AHORA))
                .isInstanceOf(FeedbackLengthException.class);
        assertThatThrownBy(() -> pendiente.editedTo(
                "Ahora si entren a spam.com por favor", SIN_BANCO, AHORA))
                .isInstanceOf(FeedbackContainsUrlException.class);
    }

    @Test
    void un_comentario_respondido_no_se_puede_editar() {
        Feedback respondido = nuevo("Texto original del comentario")
                .answeredWith("Gracias por el aviso, ya lo revisamos", AHORA.plusSeconds(60));

        assertThatThrownBy(() -> respondido.editedTo("Otro texto del comentario", SIN_BANCO, AHORA))
                .isInstanceOf(FeedbackAlreadyAnsweredException.class)
                .hasMessageContaining("ya fue respondido");
    }

    @Test
    void un_comentario_respondido_no_se_puede_borrar() {
        Feedback respondido = nuevo("Texto original del comentario")
                .answeredWith("Gracias por el aviso, ya lo revisamos", AHORA.plusSeconds(60));

        assertThatThrownBy(respondido::requirePending)
                .isInstanceOf(FeedbackAlreadyAnsweredException.class);
    }

    @Test
    void un_comentario_pendiente_si_pasa_la_comprobacion_de_estado() {
        nuevo("Texto original del comentario").requirePending();
    }

    // --- SCRUM-99 y SCRUM-100: responder y corregir la respuesta ----------------

    @Test
    void responder_pasa_el_comentario_a_respondido_y_fija_la_fecha() {
        Instant respondidoEn = AHORA.plusSeconds(3600);
        Feedback respondido = nuevo("La ruta no pasa por mi barrio")
                .answeredWith("Ya agregamos el barrio al recorrido", respondidoEn);

        assertThat(respondido.getStatus()).isEqualTo(FeedbackStatus.ANSWERED);
        assertThat(respondido.isPending()).isFalse();
        assertThat(respondido.getReply()).isEqualTo("Ya agregamos el barrio al recorrido");
        assertThat(respondido.getAnsweredAt()).isEqualTo(respondidoEn);
        assertThat(respondido.getUpdatedAt()).isEqualTo(respondidoEn);
        // El texto del usuario no se toca al responder.
        assertThat(respondido.getText()).isEqualTo("La ruta no pasa por mi barrio");
    }

    /**
     * El criterio de RF-14 dice "una respuesta por comentario; si el administrador
     * necesita corregir su respuesta, la edita, no crea una segunda". Aqui se
     * comprueba que responder dos veces deja UNA respuesta, la segunda.
     */
    @Test
    void responder_de_nuevo_edita_la_respuesta_y_no_crea_una_segunda() {
        Instant primera = AHORA.plusSeconds(3600);
        Instant correccion = primera.plusSeconds(7200);

        Feedback respondido = nuevo("La ruta no pasa por mi barrio")
                .answeredWith("Lo revisamos manana", primera)
                .answeredWith("Ya quedo agregado el barrio", correccion);

        assertThat(respondido.getReply()).isEqualTo("Ya quedo agregado el barrio");
        assertThat(respondido.getStatus()).isEqualTo(FeedbackStatus.ANSWERED);
        // answeredAt se mueve a la correccion: es lo que hace que el contador de
        // respuestas nuevas (SCRUM-101) vuelva a avisarle al usuario del cambio.
        assertThat(respondido.getAnsweredAt()).isEqualTo(correccion);
    }

    @Test
    void la_respuesta_no_puede_ir_vacia() {
        Feedback pendiente = nuevo("La ruta no pasa por mi barrio");

        assertThatThrownBy(() -> pendiente.answeredWith("   ", AHORA))
                .isInstanceOf(InvalidFeedbackReplyException.class);
        assertThatThrownBy(() -> pendiente.answeredWith(null, AHORA))
                .isInstanceOf(InvalidFeedbackReplyException.class);
    }

    @Test
    void la_respuesta_no_puede_pasar_de_quinientos_caracteres() {
        Feedback pendiente = nuevo("La ruta no pasa por mi barrio");

        assertThat(pendiente.answeredWith("a".repeat(500), AHORA).getReply()).hasSize(500);
        assertThatThrownBy(() -> pendiente.answeredWith("a".repeat(501), AHORA))
                .isInstanceOf(InvalidFeedbackReplyException.class);
    }

    /**
     * La respuesta del administrador NO se valida contra el banco de palabras ni
     * contra el limite de 100 caracteres del comentario. Es deliberado: son reglas
     * para lo que escribe el publico, no para lo que responde quien administra.
     */
    @Test
    void la_respuesta_no_se_valida_contra_el_banco_de_palabras_del_comentario() {
        Feedback respondido = Feedback.create(AUTOR, "Ana", "Comentario normal del usuario",
                        Set.of("idiota"), AHORA)
                .answeredWith("Disculpas, el conductor dijo idiota y ya fue sancionado", AHORA);

        assertThat(respondido.getReply()).contains("idiota");
    }

    // --- Coherencia de la fila al rehidratar desde la base ----------------------

    @Test
    void rehidratar_un_respondido_sin_respuesta_es_una_fila_imposible() {
        assertThatThrownBy(() -> new Feedback(1L, AUTOR, "Ana", "Comentario cualquiera aqui",
                FeedbackStatus.ANSWERED, null, AHORA, AHORA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehidratar_un_pendiente_con_respuesta_es_una_fila_imposible() {
        assertThatThrownBy(() -> new Feedback(1L, AUTOR, "Ana", "Comentario cualquiera aqui",
                FeedbackStatus.PENDING, "respuesta", null, AHORA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Rehidratar NO revalida el texto. Si manana el banco de palabras crece, los
     * comentarios ya guardados tienen que poder seguir leyendose: lo contrario
     * dejaria la pantalla del usuario rota por un cambio en un archivo de datos.
     */
    @Test
    void rehidratar_no_revalida_el_texto_que_ya_estaba_guardado() {
        Feedback deLaBase = new Feedback(1L, AUTOR, "Ana", "corto",
                FeedbackStatus.PENDING, null, null, AHORA, AHORA);

        assertThat(deLaBase.getText()).isEqualTo("corto");
    }

    @Test
    void with_id_conserva_todo_lo_demas() {
        Feedback conId = nuevo("Comentario recien guardado aqui").withId(42L);

        assertThat(conId.getId()).isEqualTo(42L);
        assertThat(conId.getText()).isEqualTo("Comentario recien guardado aqui");
        assertThat(conId.getStatus()).isEqualTo(FeedbackStatus.PENDING);
    }
}
