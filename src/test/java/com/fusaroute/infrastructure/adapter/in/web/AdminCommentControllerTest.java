package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.FeedbackNotFoundException;
import com.fusaroute.domain.exception.InvalidFeedbackReplyException;
import com.fusaroute.domain.model.Feedback;
import com.fusaroute.domain.model.FeedbackStatus;
import com.fusaroute.domain.port.in.ListAllFeedbackUseCase;
import com.fusaroute.domain.port.in.ReplyToFeedbackUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del panel de comentarios del administrador (RF-14, SCRUM-23).
 *
 * La mitad de este archivo es sobre AUTORIZACION (SCRUM-104), y es la mitad que
 * importa: que un USER autenticado reciba 403 y no una lista de comentarios. Se prueba
 * con el {@code SecurityConfig} real importado, no con la seguridad desactivada —
 * probar un panel de administrador sin la cadena de filtros verificaria exactamente
 * lo que no hay que verificar.
 */
@WebMvcTest(AdminCommentController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class AdminCommentControllerTest {

    private static final Instant AHORA = Instant.parse("2026-11-07T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListAllFeedbackUseCase listAllFeedback;

    @MockitoBean
    private ReplyToFeedbackUseCase replyToFeedback;

    private static Feedback pendiente(Long id) {
        return new Feedback(id, 7L, "Ana", "Comentario pendiente de Ana",
                FeedbackStatus.PENDING, null, null, AHORA, AHORA);
    }

    private static Feedback respondido(Long id) {
        return new Feedback(id, 8L, "Beto", "Comentario respondido de Beto",
                FeedbackStatus.ANSWERED, "Ya lo revisamos", AHORA, AHORA, AHORA);
    }

    /**
     * Las authorities van EXPLICITAS y no se dejan deducir de los claims, y conviene
     * saber por que: el {@code jwt()} de Spring Security Test no usa nuestro
     * {@code jwtAuthenticationConverter}. Trae su propio conversor por defecto, que
     * lee el claim {@code scope} —de ahi que los tests del resto del proyecto
     * funcionen poniendo solo {@code scope}— pero NO sabe nada del claim
     * {@code role}. Con solo los claims, un token de administrador llegaria sin
     * {@code ROLE_ADMIN} y todo responderia 403.
     *
     * Lo que estos helpers construyen es exactamente lo que produce el converter de
     * produccion para ese par de claims, y aqui se verifica la REGLA de autorizacion
     * sobre {@code /api/admin/**}. Que el converter traduzca bien el claim a la
     * authority es otra cosa, y tiene su propio test en
     * {@code SecurityConfigAuthoritiesTest}: separar las dos es lo que evita un test
     * que pase por las razones equivocadas.
     */
    private static RequestPostProcessor tokenCon(String... authorities) {
        return tokenDe("1", authorities);
    }

    /**
     * Igual que {@link #tokenCon} pero con el id de usuario del {@code sub}. El
     * controlador lo lee para calcular {@code mine}, asi que tiene que ser numerico:
     * el que pone {@code jwt()} por defecto no lo es.
     */
    private static RequestPostProcessor tokenDe(String subject, String... authorities) {
        return jwt().jwt(j -> j.subject(subject)).authorities(java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList());
    }

    /** Token de administrador: scope full + rol ADMIN, las dos cosas. */
    private static RequestPostProcessor admin() {
        return tokenCon("SCOPE_full", "ROLE_ADMIN");
    }

    /** Administrador con un id de usuario concreto, para probar {@code mine}. */
    private static RequestPostProcessor adminDe(String subject) {
        return tokenDe(subject, "SCOPE_full", "ROLE_ADMIN");
    }

    /** Token de usuario normal: autenticado, pero sin rol de administrador. */
    private static RequestPostProcessor usuario() {
        return tokenCon("SCOPE_full", "ROLE_USER");
    }

    // --- SCRUM-104: autorizacion por rol ----------------------------------------

    /**
     * El criterio de aceptacion que sostiene todo lo demas: un USER autenticado NO
     * entra al panel. 403 y no 401, porque el token es valido y lo que falta es el
     * permiso.
     */
    @Test
    void un_usuario_sin_rol_de_administrador_recibe_403_en_el_listado() throws Exception {
        mockMvc.perform(get("/api/admin/comments").with(usuario()))
                .andExpect(status().isForbidden());

        // Y el caso de uso no se llega a ejecutar: el filtro corta antes.
        verifyNoInteractions(listAllFeedback);
    }

    @Test
    void un_usuario_sin_rol_de_administrador_recibe_403_al_responder() throws Exception {
        mockMvc.perform(put("/api/admin/comments/5/reply")
                        .with(usuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Respuesta que no deberia pasar\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(replyToFeedback);
    }

    /**
     * Un token SIN el claim {@code role} tampoco entra. Importa porque el converter
     * trata un {@code scope} ausente como "full" por compatibilidad hacia atras, y
     * habria sido facil darle el mismo trato al rol: ante la duda, sin privilegios.
     */
    @Test
    void un_token_sin_claim_de_rol_no_entra_al_panel() throws Exception {
        mockMvc.perform(get("/api/admin/comments").with(tokenCon("SCOPE_full")))
                .andExpect(status().isForbidden());
    }

    /**
     * Un token de ENROLAMIENTO de una cuenta ADMIN tampoco abre el panel. Es el caso
     * que obliga a exigir rol Y scope juntos: con {@code hasAnyAuthority} bastaria el
     * rol, y el alcance de ese token deberia terminar en los dos endpoints de TOTP.
     */
    @Test
    void un_token_de_enrolamiento_aunque_sea_de_un_admin_no_abre_el_panel() throws Exception {
        mockMvc.perform(get("/api/admin/comments")
                        .with(tokenCon("SCOPE_totp_enroll", "ROLE_ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(listAllFeedback);
    }

    @Test
    void sin_token_el_panel_responde_401_y_no_403() throws Exception {
        mockMvc.perform(get("/api/admin/comments"))
                .andExpect(status().isUnauthorized());
    }

    // --- SCRUM-98: listado ------------------------------------------------------

    @Test
    void el_administrador_ve_todos_los_comentarios_con_su_estado() throws Exception {
        when(listAllFeedback.allFeedbackPendingFirst())
                .thenReturn(List.of(pendiente(1L), respondido(2L)));

        mockMvc.perform(get("/api/admin/comments").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].status").value("ANSWERED"))
                .andExpect(jsonPath("$[1].reply").value("Ya lo revisamos"));
    }

    /**
     * El administrador necesita ver de QUIEN es cada comentario: el criterio de RF-08
     * dice que no es anonimo, y responder sin saber a quien no tiene sentido.
     */
    @Test
    void el_listado_del_administrador_trae_el_nombre_de_cada_autor() throws Exception {
        when(listAllFeedback.allFeedbackPendingFirst())
                .thenReturn(List.of(pendiente(1L), respondido(2L)));

        mockMvc.perform(get("/api/admin/comments").with(admin()))
                .andExpect(jsonPath("$[0].authorName").value("Ana"))
                .andExpect(jsonPath("$[1].authorName").value("Beto"));
    }

    @Test
    void sin_filtro_el_listado_sale_con_los_pendientes_primero() throws Exception {
        when(listAllFeedback.allFeedbackPendingFirst()).thenReturn(List.of(pendiente(1L)));

        mockMvc.perform(get("/api/admin/comments").with(admin()))
                .andExpect(status().isOk());

        verify(listAllFeedback).allFeedbackPendingFirst();
        verify(listAllFeedback, never()).allFeedbackWithStatus(any());
    }

    @Test
    void con_status_pendiente_el_listado_filtra_por_ese_estado() throws Exception {
        when(listAllFeedback.allFeedbackWithStatus(FeedbackStatus.PENDING))
                .thenReturn(List.of(pendiente(1L)));

        mockMvc.perform(get("/api/admin/comments").param("status", "PENDING").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(listAllFeedback).allFeedbackWithStatus(FeedbackStatus.PENDING);
        verify(listAllFeedback, never()).allFeedbackPendingFirst();
    }

    @Test
    void con_status_respondido_el_listado_filtra_por_ese_estado() throws Exception {
        when(listAllFeedback.allFeedbackWithStatus(FeedbackStatus.ANSWERED))
                .thenReturn(List.of(respondido(2L)));

        mockMvc.perform(get("/api/admin/comments").param("status", "ANSWERED").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ANSWERED"));
    }

    @Test
    void un_status_que_no_existe_es_un_400_y_no_un_500() throws Exception {
        mockMvc.perform(get("/api/admin/comments").param("status", "ARCHIVADO").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sin_comentarios_el_panel_devuelve_lista_vacia_no_un_404() throws Exception {
        when(listAllFeedback.allFeedbackPendingFirst()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/comments").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- SCRUM-99 y SCRUM-100: responder ----------------------------------------

    @Test
    void responder_devuelve_200_con_el_comentario_ya_respondido() throws Exception {
        when(replyToFeedback.reply(5L, "Ya agregamos el barrio al recorrido"))
                .thenReturn(respondido(5L));

        mockMvc.perform(put("/api/admin/comments/5/reply")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Ya agregamos el barrio al recorrido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.reply").value("Ya lo revisamos"))
                // Respondido => el autor ya no puede editarlo. El panel y la pantalla
                // del usuario leen el mismo campo.
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void la_respuesta_del_administrador_incluye_mine_false_en_el_comentario_de_otro() throws Exception {
        when(replyToFeedback.reply(anyLong(), any())).thenReturn(respondido(5L)); // autor: usuario 8

        mockMvc.perform(put("/api/admin/comments/5/reply")
                        .with(adminDe("1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Ya agregamos el barrio al recorrido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.editable").value(false));
    }

    /**
     * Un administrador tambien deja comentarios: al responder el suyo, {@code mine} es
     * true (y {@code editable} sigue false, porque respondido bloquea).
     */
    @Test
    void al_responder_su_propio_comentario_el_administrador_ve_mine_true() throws Exception {
        when(replyToFeedback.reply(anyLong(), any())).thenReturn(respondido(5L)); // autor: usuario 8

        mockMvc.perform(put("/api/admin/comments/5/reply")
                        .with(adminDe("8"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Respuesta a mi propio comentario\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void el_listado_del_administrador_marca_mine_solo_en_sus_propios_comentarios() throws Exception {
        when(listAllFeedback.allFeedbackPendingFirst())
                .thenReturn(List.of(pendiente(1L), respondido(2L))); // autores: 7 y 8

        mockMvc.perform(get("/api/admin/comments").with(adminDe("7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mine").value(true))
                .andExpect(jsonPath("$[0].editable").value(true))
                .andExpect(jsonPath("$[1].mine").value(false));
    }

    @Test
    void responder_un_comentario_que_no_existe_responde_404() throws Exception {
        when(replyToFeedback.reply(anyLong(), any()))
                .thenThrow(new FeedbackNotFoundException("El comentario no existe"));

        mockMvc.perform(put("/api/admin/comments/404/reply")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Respuesta cualquiera\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void una_respuesta_vacia_responde_400_con_el_mensaje_del_dominio() throws Exception {
        when(replyToFeedback.reply(anyLong(), any()))
                .thenThrow(new InvalidFeedbackReplyException("La respuesta no puede ir vacia"));

        mockMvc.perform(put("/api/admin/comments/5/reply")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La respuesta no puede ir vacia"));
    }

    /**
     * Responder un comentario que YA tenia respuesta NO es un 409: corregir es lo que
     * el criterio permite. El 409 es solo para el AUTOR que intenta editar su
     * comentario ya respondido.
     */
    @Test
    void responder_un_comentario_ya_respondido_es_200_y_no_409() throws Exception {
        when(replyToFeedback.reply(5L, "Correccion de la respuesta anterior"))
                .thenReturn(respondido(5L));

        mockMvc.perform(put("/api/admin/comments/5/reply")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Correccion de la respuesta anterior\"}"))
                .andExpect(status().isOk());
    }
}
