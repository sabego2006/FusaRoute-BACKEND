package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.SearchRecord;

import java.util.List;

/**
 * Devuelve el historial de busquedas del usuario (RF-07, SCRUM-20), de la mas
 * reciente a la mas antigua.
 */
public interface GetSearchHistoryUseCase {

    List<SearchRecord> historyOf(Long userId);
}
