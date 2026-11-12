package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.FeedbackPage;

/**
 * Feed compartido de comentarios: los de TODOS los usuarios, pendientes y
 * respondidos, visibles para cualquier usuario autenticado.
 *
 * A diferencia de {@link ListAllFeedbackUseCase} (panel del administrador, pendientes
 * primero y sin paginar), este va ordenado solo por fecha y paginado: es una pantalla
 * de lectura abierta a todos y crece con cada comentario.
 */
public interface ListFeedbackFeedUseCase {

    /**
     * Una pagina del feed, del comentario mas reciente al mas antiguo; a igual fecha,
     * el de id mayor primero para que el orden sea estable entre paginas.
     *
     * Un {@code size} mayor que {@link FeedbackPage#MAX_SIZE} se recorta a ese tope.
     *
     * @throws com.fusaroute.domain.exception.InvalidFeedbackPageException si
     *         {@code page} es negativa o {@code size} es menor que 1.
     */
    FeedbackPage feed(int page, int size);
}
