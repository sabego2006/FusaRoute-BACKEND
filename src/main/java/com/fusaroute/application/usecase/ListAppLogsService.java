package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.AppLog;
import com.fusaroute.domain.model.LogLevel;
import com.fusaroute.domain.port.in.ListAppLogsUseCase;
import com.fusaroute.domain.port.out.AppLogQueryPort;

import java.time.Instant;
import java.util.List;

public class ListAppLogsService implements ListAppLogsUseCase {

    private final AppLogQueryPort logs;

    public ListAppLogsService(AppLogQueryPort logs) {
        this.logs = logs;
    }

    @Override
    public List<AppLog> list(LogLevel level, Instant since, Integer limit) {
        return logs.findRecent(level, since, MonitoringLimit.resolve(limit));
    }
}
