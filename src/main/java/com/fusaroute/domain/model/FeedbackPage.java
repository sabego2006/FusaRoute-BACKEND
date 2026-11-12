package com.fusaroute.domain.model;

import java.util.List;

/**
 * Una pagina del feed de comentarios y lo necesario para pedir la siguiente.
 *
 * Paginacion por desplazamiento ({@code page} base 0). {@code size} es el tamano
 * EFECTIVO, ya recortado al maximo: es el que el cliente debe usar para seguir
 * paginando, no necesariamente el que pidio.
 *
 * @param items      comentarios de esta pagina, del mas reciente al mas antiguo.
 * @param totalItems cuantos comentarios hay en total, de todos los usuarios.
 */
public record FeedbackPage(List<Feedback> items, int page, int size, long totalItems) {

    /** Tamano cuando el cliente no manda {@code size}. */
    public static final int DEFAULT_SIZE = 20;

    /** Tope de {@code size}: un valor mayor se recorta a este. */
    public static final int MAX_SIZE = 50;

    public FeedbackPage {
        items = List.copyOf(items);
    }

    /**
     * Hay paginas despues de esta. Se calcula contra el total y no contra
     * {@code items.size() == size}: con esto ultimo, una ultima pagina exactamente
     * llena anunciaria una pagina siguiente vacia.
     */
    public boolean hasMore() {
        return (long) (page + 1) * size < totalItems;
    }
}
