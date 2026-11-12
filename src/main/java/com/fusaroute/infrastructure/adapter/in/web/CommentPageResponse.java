package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.FeedbackPage;

import java.util.List;

/**
 * Una pagina del feed compartido de comentarios.
 *
 * @param page       numero de pagina devuelto, base 0.
 * @param size       tamano EFECTIVO de pagina, ya recortado al maximo: el cliente debe
 *                   seguir paginando con este valor, no con el que pidio.
 * @param totalItems comentarios en total, de todos los usuarios.
 * @param hasMore    hay una pagina siguiente.
 */
public record CommentPageResponse(List<CommentResponse> items, int page, int size,
                                  long totalItems, boolean hasMore) {

    public static CommentPageResponse from(FeedbackPage feedbackPage, Long viewerId) {
        return new CommentPageResponse(
                feedbackPage.items().stream().map(f -> CommentResponse.from(f, viewerId)).toList(),
                feedbackPage.page(),
                feedbackPage.size(),
                feedbackPage.totalItems(),
                feedbackPage.hasMore());
    }
}
