package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.AppLog;

import java.time.Instant;

/** Una fila de {@code app_logs} en JSON (SCRUM-186). */
public record AppLogResponse(
        Long id,
        Instant ts,
        String level,
        String logger,
        String message,
        String exception,
        String thread,
        String profile) {

    public static AppLogResponse from(AppLog log) {
        return new AppLogResponse(log.id(), log.ts(), log.level().name(), log.logger(), log.message(),
                log.exception(), log.thread(), log.profile());
    }
}
