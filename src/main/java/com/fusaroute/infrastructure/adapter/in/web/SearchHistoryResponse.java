package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.SearchRecord;

import java.time.Instant;

/**
 * Una entrada del historial tal como la ve el cliente (RF-07, SCRUM-20).
 *
 * No lleva {@code userId}: el endpoint ya es "mi historial" y devolver el id del
 * dueno no le aporta nada al frontend.
 */
public record SearchHistoryResponse(Long id, String origin, String destination, Instant searchedAt) {

    public static SearchHistoryResponse from(SearchRecord record) {
        return new SearchHistoryResponse(record.id(), record.origin(), record.destination(), record.searchedAt());
    }
}
