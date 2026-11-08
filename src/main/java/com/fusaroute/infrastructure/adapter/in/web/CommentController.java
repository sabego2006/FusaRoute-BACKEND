package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.CountUnreadRepliesUseCase;
import com.fusaroute.domain.port.in.DeleteFeedbackUseCase;
import com.fusaroute.domain.port.in.EditFeedbackUseCase;
import com.fusaroute.domain.port.in.ListMyFeedbackUseCase;
import com.fusaroute.domain.port.in.SubmitFeedbackUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Caja de comentarios del usuario autenticado (RF-08, SCRUM-22):
 * <ul>
 *   <li>POST   /api/comments                  → dejar un comentario (SCRUM-92)</li>
 *   <li>GET    /api/comments/me               → mis comentarios con estado y respuesta</li>
 *   <li>PUT    /api/comments/{id}             → editar, solo si sigue pendiente (SCRUM-94)</li>
 *   <li>DELETE /api/comments/{id}             → borrar, solo si sigue pendiente (SCRUM-95)</li>
 *   <li>GET    /api/comments/me/unread-count  → respuestas nuevas desde el ingreso anterior (SCRUM-101)</li>
 * </ul>
 *
 * La ruta es {@code /api/comments} aunque el modelo de dominio se llame
 * {@code Feedback}: la URL es lenguaje de usuario, y el usuario deja "comentarios".
 * El nombre del modelo lo fija el CLAUDE.md del repo.
 *
 * Todos exigen sesion. El id del usuario sale del claim {@code sub} del JWT y nunca
 * del cuerpo ni de la URL: si viniera de ahi, cualquiera podria leer o borrar los
 * comentarios de otro cambiando un numero.
 *
 * Este controlador NO valida nada. Longitud, enlaces, banco de palabras, autoria y
 * bloqueo tras la respuesta son del dominio, y {@link GlobalExceptionHandler}
 * traduce cada excepcion a su codigo HTTP.
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final SubmitFeedbackUseCase submitFeedback;
    private final ListMyFeedbackUseCase listMyFeedback;
    private final EditFeedbackUseCase editFeedback;
    private final DeleteFeedbackUseCase deleteFeedback;
    private final CountUnreadRepliesUseCase countUnreadReplies;

    public CommentController(SubmitFeedbackUseCase submitFeedback,
                             ListMyFeedbackUseCase listMyFeedback,
                             EditFeedbackUseCase editFeedback,
                             DeleteFeedbackUseCase deleteFeedback,
                             CountUnreadRepliesUseCase countUnreadReplies) {
        this.submitFeedback = submitFeedback;
        this.listMyFeedback = listMyFeedback;
        this.editFeedback = editFeedback;
        this.deleteFeedback = deleteFeedback;
        this.countUnreadReplies = countUnreadReplies;
    }

    @Operation(summary = "Dejar un comentario",
            description = "Guarda un comentario del usuario autenticado. Debe tener entre 10 y 100 "
                    + "caracteres, no puede incluir enlaces y se valida contra un banco de palabras "
                    + "prohibidas (RF-08).")
    @ApiResponse(responseCode = "201", description = "Comentario creado exitosamente")
    @ApiResponse(responseCode = "400", description = "Texto demasiado corto o largo, con enlaces o con lenguaje no permitido")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse submit(@AuthenticationPrincipal Jwt jwt,
                                  @RequestBody CommentRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return CommentResponse.from(submitFeedback.submit(userId, request.text()));
    }

    @Operation(summary = "Mis comentarios",
            description = "Comentarios del usuario autenticado, del más reciente al más antiguo, "
                    + "con su estado y la respuesta del administrador si ya la tiene (RF-08).")
    @ApiResponse(responseCode = "200", description = "Comentarios recuperados exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping("/me")
    public List<CommentResponse> myComments(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        // Lista vacia y no 404 cuando no hay comentarios: no haber escrito ninguno es
        // el estado normal de una cuenta nueva, no un recurso que falte.
        return listMyFeedback.myFeedback(userId).stream().map(CommentResponse::from).toList();
    }

    @Operation(summary = "Editar mi comentario",
            description = "Reemplaza el texto de un comentario propio. Solo se permite mientras el "
                    + "comentario siga pendiente de respuesta (RF-08).")
    @ApiResponse(responseCode = "200", description = "Comentario actualizado exitosamente")
    @ApiResponse(responseCode = "400", description = "El texto nuevo no cumple la validación")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "El comentario no existe o no es del usuario")
    @ApiResponse(responseCode = "409", description = "El comentario ya fue respondido y quedó bloqueado")
    @PutMapping("/{id}")
    public CommentResponse edit(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable Long id,
                                @RequestBody CommentRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return CommentResponse.from(editFeedback.edit(userId, id, request.text()));
    }

    @Operation(summary = "Borrar mi comentario",
            description = "Borra un comentario propio. Solo se permite mientras el comentario siga "
                    + "pendiente de respuesta (RF-08).")
    @ApiResponse(responseCode = "204", description = "Comentario borrado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "El comentario no existe o no es del usuario")
    @ApiResponse(responseCode = "409", description = "El comentario ya fue respondido y quedó bloqueado")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        deleteFeedback.delete(Long.parseLong(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Respuestas nuevas",
            description = "Cuántos comentarios del usuario fueron respondidos desde su ingreso "
                    + "anterior (RF-14). Es un chequeo del backend que el frontend hace al cargar la "
                    + "sesión: las notificaciones push están fuera de alcance.")
    @ApiResponse(responseCode = "200", description = "Conteo recuperado exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @GetMapping("/me/unread-count")
    public UnreadRepliesResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return new UnreadRepliesResponse(countUnreadReplies.unreadReplies(userId));
    }
}
