package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.FeedbackStatus;
import com.fusaroute.domain.port.in.ListAllFeedbackUseCase;
import com.fusaroute.domain.port.in.ReplyToFeedbackUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Comentarios desde el panel del administrador (RF-14, SCRUM-23):
 * <ul>
 *   <li>GET /api/admin/comments            → todos, pendientes primero, filtrable (SCRUM-98)</li>
 *   <li>PUT /api/admin/comments/{id}/reply → responder o corregir la respuesta (SCRUM-99, SCRUM-100)</li>
 * </ul>
 *
 * <p><b>Este controlador no comprueba el rol, y eso es correcto.</b> La regla del repo
 * dice que la verificacion de rol va en la capa de seguridad y no dispersa en los
 * controladores: {@code SecurityConfig} exige rol ADMIN sobre todo
 * {@code /api/admin/**} (SCRUM-104). Tenerlo en un solo lugar es lo que hace que
 * agregar un endpoint de administrador manana quede protegido sin que nadie se
 * acuerde de anotarlo.
 *
 * <p>Va en {@code /api/admin/comments} y no en {@code /api/comments/admin} a
 * proposito: el prefijo {@code /api/admin/} es lo que un solo patron de Spring
 * Security puede cerrar de una vez. Con la autorizacion repartida por sufijos haria
 * falta enumerar cada ruta, y la que se olvide queda abierta a cualquier usuario
 * autenticado.
 *
 * <p>El id del administrador no se usa: la respuesta no se atribuye a una persona en
 * el alcance actual. Ver {@link ReplyToFeedbackUseCase}.
 */
@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

    private final ListAllFeedbackUseCase listAllFeedback;
    private final ReplyToFeedbackUseCase replyToFeedback;

    public AdminCommentController(ListAllFeedbackUseCase listAllFeedback,
                                  ReplyToFeedbackUseCase replyToFeedback) {
        this.listAllFeedback = listAllFeedback;
        this.replyToFeedback = replyToFeedback;
    }

    @Operation(summary = "Listado de comentarios (administrador)",
            description = "Todos los comentarios con su estado. Sin filtro salen los pendientes "
                    + "primero y, dentro de cada grupo, del más reciente al más antiguo. Con "
                    + "?status=PENDING o ?status=ANSWERED salen solo los de ese estado (RF-14).")
    @ApiResponse(responseCode = "200", description = "Comentarios recuperados exitosamente")
    @ApiResponse(responseCode = "400", description = "El valor de status no es un estado válido")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @GetMapping
    public List<CommentResponse> all(@RequestParam(required = false) FeedbackStatus status) {
        // Spring convierte el texto del parametro al enum; un valor que no existe
        // termina en 400 por MethodArgumentTypeMismatchException, que ya traduce
        // GlobalExceptionHandler. No hace falta validarlo a mano.
        var comments = status == null
                ? listAllFeedback.allFeedbackPendingFirst()
                : listAllFeedback.allFeedbackWithStatus(status);
        return comments.stream().map(CommentResponse::from).toList();
    }

    @Operation(summary = "Responder un comentario",
            description = "Deja la respuesta del administrador y pasa el comentario a respondido, "
                    + "lo que lo bloquea para edición y borrado por parte del autor. Si el "
                    + "comentario ya tenía respuesta, la REEMPLAZA: una respuesta por comentario, "
                    + "no un hilo (RF-14).")
    @ApiResponse(responseCode = "200", description = "Comentario respondido exitosamente")
    @ApiResponse(responseCode = "400", description = "La respuesta va vacía o pasa de 500 caracteres")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @ApiResponse(responseCode = "404", description = "El comentario no existe")
    @PutMapping("/{id}/reply")
    public CommentResponse reply(@PathVariable Long id, @RequestBody FeedbackReplyRequest request) {
        // PUT y no POST: responder es idempotente en su efecto. Mandar la misma
        // respuesta dos veces deja el comentario igual, y eso es justo lo que pide
        // "una respuesta por comentario, no un hilo".
        return CommentResponse.from(replyToFeedback.reply(id, request.reply()));
    }
}
