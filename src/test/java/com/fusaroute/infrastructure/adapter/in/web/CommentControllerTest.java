package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.FeedbackAlreadyAnsweredException;
import com.fusaroute.domain.exception.FeedbackContainsUrlException;
import com.fusaroute.domain.exception.FeedbackForbiddenWordException;
import com.fusaroute.domain.exception.FeedbackLengthException;
import com.fusaroute.domain.exception.FeedbackNotFoundException;
import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;
import com.fusaroute.domain.port.in.CountUnreadRepliesUseCase;
import com.fusaroute.domain.port.in.DeleteFeedbackUseCase;
import com.fusaroute.domain.port.in.EditFeedbackUseCase;
import com.fusaroute.domain.port.in.ListMyFeedbackUseCase;
import com.fusaroute.domain.port.in.SubmitFeedbackUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de la caja de comentarios (RF-08, SCRUM-22).
 *
 * Comprueba los codigos que el frontend tiene que distinguir —201, 400, 401, 404 y
 * 409— y que el id del usuario salga del JWT y no del cuerpo. Las reglas de negocio
 * se prueban en {@code FeedbackTest} y {@code FeedbackServiceTest}; aqui los casos de
 * uso van mockeados.
 */
@WebMvcTest(CommentController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class CommentControllerTest {

    private static final Instant AHORA = Instant.parse("2026-11-07T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitFeedbackUseCase submitFeedback;

    @MockitoBean
    private ListMyFeedbackUseCase listMyFeedback;

    @MockitoBean
    private EditFeedbackUseCase editFeedback;

    @MockitoBean
    private DeleteFeedbackUseCase deleteFeedback;

    @MockitoBean
    private CountUnreadRepliesUseCase countUnreadReplies;

    private static Feedback pendiente() {
        return new Feedback(5L, 1L, "Ana", "Comentario pendiente de Ana",
                FeedbackStatus.PENDING, null, null, AHORA, AHORA);
    }

    private static Feedback respondido() {
        return new Feedback(6L, 1L, "Ana", "Comentario respondido de Ana",
                FeedbackStatus.ANSWERED, "Ya lo revisamos", AHORA, AHORA, AHORA);
    }

    // --- SCRUM-92: POST /api/comments -------------------------------------------

    @Test
    void crear_un_comentario_responde_201_con_el_comentario_creado() throws Exception {
        when(submitFeedback.submit(1L, "La ruta deberia pasar mas seguido")).thenReturn(pendiente());

        mockMvc.perform(post("/api/comments")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"La ruta deberia pasar mas seguido\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.reply").doesNotExist());
    }

    /**
     * El criterio pide que el comentario NO sea anonimo: la respuesta trae el nombre
     * del autor.
     */
    @Test
    void el_comentario_devuelve_el_nombre_del_autor() throws Exception {
        when(submitFeedback.submit(anyLong(), any())).thenReturn(pendiente());

        mockMvc.perform(post("/api/comments")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Comentario cualquiera valido\"}"))
                .andExpect(jsonPath("$.authorName").value("Ana"));
    }

    /**
     * El id interno de la cuenta no se expone, igual que en el historial de
     * busquedas: el nombre ya identifica al autor para lo que la interfaz necesita.
     */
    @Test
    void el_comentario_no_expone_el_id_del_dueno() throws Exception {
        when(submitFeedback.submit(anyLong(), any())).thenReturn(pendiente());

        mockMvc.perform(post("/api/comments")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Comentario cualquiera valido\"}"))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void el_id_del_usuario_sale_del_token_y_no_del_cuerpo() throws Exception {
        when(submitFeedback.submit(anyLong(), any())).thenReturn(pendiente());

        // El cuerpo trae un userId distinto a proposito: debe ignorarse.
        mockMvc.perform(post("/api/comments")
                        .with(jwt().jwt(j -> j.subject("42").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Comentario cualquiera valido\",\"userId\":999}"))
                .andExpect(status().isCreated());

        verify(submitFeedback).submit(42L, "Comentario cualquiera valido");
    }

    @Test
    void un_comentario_demasiado_corto_responde_400_con_el_mensaje_del_dominio() throws Exception {
        when(submitFeedback.submit(anyLong(), any()))
                .thenThrow(new FeedbackLengthException("El comentario debe tener entre 10 y 100 caracteres"));

        mockMvc.perform(post("/api/comments")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"corto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El comentario debe tener entre 10 y 100 caracteres"));
    }

    @Test
    void un_comentario_con_enlace_responde_400() throws Exception {
        when(submitFeedback.submit(anyLong(), any()))
                .thenThrow(new FeedbackContainsUrlException("El comentario no puede incluir enlaces"));

        mockMvc.perform(post("/api/comments")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Entren a spam.com ya mismo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El comentario no puede incluir enlaces"));
    }

    @Test
    void un_comentario_con_palabra_prohibida_responde_400() throws Exception {
        when(submitFeedback.submit(anyLong(), any()))
                .thenThrow(new FeedbackForbiddenWordException("El comentario incluye lenguaje que no se permite"));

        mockMvc.perform(post("/api/comments")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Comentario con groseria aqui\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_un_comentario_exige_sesion() throws Exception {
        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Comentario cualquiera valido\"}"))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/comments/me ---------------------------------------------------

    @Test
    void devuelve_mis_comentarios_con_estado_y_respuesta() throws Exception {
        when(listMyFeedback.myFeedback(1L)).thenReturn(List.of(respondido(), pendiente()));

        mockMvc.perform(get("/api/comments/me")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("ANSWERED"))
                .andExpect(jsonPath("$[0].reply").value("Ya lo revisamos"))
                // Respondido => bloqueado para el autor.
                .andExpect(jsonPath("$[0].editable").value(false))
                .andExpect(jsonPath("$[1].status").value("PENDING"))
                .andExpect(jsonPath("$[1].editable").value(true));
    }

    @Test
    void sin_comentarios_devuelve_una_lista_vacia_no_un_404() throws Exception {
        when(listMyFeedback.myFeedback(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/comments/me")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void mis_comentarios_exige_sesion() throws Exception {
        mockMvc.perform(get("/api/comments/me"))
                .andExpect(status().isUnauthorized());
    }

    // --- SCRUM-94: PUT /api/comments/{id} ---------------------------------------

    @Test
    void editar_un_comentario_pendiente_responde_200() throws Exception {
        when(editFeedback.edit(1L, 5L, "Comentario corregido por Ana")).thenReturn(pendiente());

        mockMvc.perform(put("/api/comments/5")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Comentario corregido por Ana\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void editar_un_comentario_ajeno_o_inexistente_responde_404() throws Exception {
        when(editFeedback.edit(anyLong(), anyLong(), any()))
                .thenThrow(new FeedbackNotFoundException("El comentario no existe"));

        mockMvc.perform(put("/api/comments/5")
                        .with(jwt().jwt(j -> j.subject("99").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Comentario de alguien mas\"}"))
                .andExpect(status().isNotFound());
    }

    /**
     * 409 y no 403: el permiso esta bien —es su comentario— y lo que choca es el
     * estado. El frontend usa esa diferencia para recargar el comentario en vez de
     * mandar al usuario a iniciar sesion.
     */
    @Test
    void editar_un_comentario_ya_respondido_responde_409() throws Exception {
        when(editFeedback.edit(anyLong(), anyLong(), any()))
                .thenThrow(new FeedbackAlreadyAnsweredException(
                        "El comentario ya fue respondido y no se puede editar ni borrar"));

        mockMvc.perform(put("/api/comments/6")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Intento de cambiar el texto\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "El comentario ya fue respondido y no se puede editar ni borrar"));
    }

    @Test
    void editar_exige_sesion() throws Exception {
        mockMvc.perform(put("/api/comments/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Comentario cualquiera valido\"}"))
                .andExpect(status().isUnauthorized());
    }

    // --- SCRUM-95: DELETE /api/comments/{id} ------------------------------------

    @Test
    void borrar_un_comentario_pendiente_responde_204() throws Exception {
        doNothing().when(deleteFeedback).delete(1L, 5L);

        mockMvc.perform(delete("/api/comments/5")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isNoContent());

        verify(deleteFeedback).delete(1L, 5L);
    }

    @Test
    void borrar_un_comentario_ya_respondido_responde_409() throws Exception {
        doThrow(new FeedbackAlreadyAnsweredException("El comentario ya fue respondido"))
                .when(deleteFeedback).delete(anyLong(), anyLong());

        mockMvc.perform(delete("/api/comments/6")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isConflict());
    }

    @Test
    void borrar_un_comentario_ajeno_o_inexistente_responde_404() throws Exception {
        doThrow(new FeedbackNotFoundException("El comentario no existe"))
                .when(deleteFeedback).delete(anyLong(), anyLong());

        mockMvc.perform(delete("/api/comments/5")
                        .with(jwt().jwt(j -> j.subject("99").claim("scope", "full"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void borrar_exige_sesion() throws Exception {
        mockMvc.perform(delete("/api/comments/5"))
                .andExpect(status().isUnauthorized());
    }

    // --- SCRUM-101: GET /api/comments/me/unread-count ---------------------------

    @Test
    void el_contador_de_respuestas_nuevas_devuelve_el_numero() throws Exception {
        when(countUnreadReplies.unreadReplies(1L)).thenReturn(3L);

        mockMvc.perform(get("/api/comments/me/unread-count")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void sin_respuestas_nuevas_el_contador_es_cero() throws Exception {
        when(countUnreadReplies.unreadReplies(1L)).thenReturn(0L);

        mockMvc.perform(get("/api/comments/me/unread-count")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void el_contador_exige_sesion() throws Exception {
        mockMvc.perform(get("/api/comments/me/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * La ruta del contador tiene dos segmentos y la de editar uno, asi que
     * {@code /me/unread-count} no se puede confundir con {@code /{id}}. Este test
     * fija eso: si alguien cambia el mapeo a {@code /{id}/...}, el contador
     * empezaria a resolverse como un comentario con id "me" y fallaria con 400.
     */
    @Test
    void la_ruta_del_contador_no_se_confunde_con_la_de_un_comentario() throws Exception {
        when(countUnreadReplies.unreadReplies(1L)).thenReturn(1L);

        mockMvc.perform(get("/api/comments/me/unread-count")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk());
    }
}
