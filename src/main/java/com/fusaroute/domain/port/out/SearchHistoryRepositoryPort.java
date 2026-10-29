package com.fusaroute.domain.port.out;

import com.fusaroute.domain.model.SearchRecord;

import java.util.List;

/**
 * Puerto de salida del historial de busquedas (RF-07, SCRUM-20).
 */
public interface SearchHistoryRepositoryPort {

    /** Guarda la busqueda y devuelve la entrada ya con id. */
    SearchRecord save(SearchRecord record);

    /**
     * Las ultimas busquedas del usuario, de la mas reciente a la mas antigua.
     * Nunca devuelve las de otro usuario.
     */
    List<SearchRecord> findRecentByUser(Long userId, int limit);

    /**
     * Borra las entradas del usuario que sobren mas alla de las {@code keep} mas
     * recientes. Se llama despues de guardar, para sostener el tope de RF-07.
     *
     * @return cuantas filas se borraron.
     */
    int deleteOlderBeyond(Long userId, int keep);
}
