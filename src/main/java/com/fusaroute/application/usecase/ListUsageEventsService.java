package com.fusaroute.application.usecase;

import com.fusaroute.domain.model.UsageEvent;
import com.fusaroute.domain.port.in.ListUsageEventsUseCase;
import com.fusaroute.domain.port.out.UsageEventQueryPort;

import java.time.Instant;
import java.util.List;

public class ListUsageEventsService implements ListUsageEventsUseCase {

    private final UsageEventQueryPort events;

    public ListUsageEventsService(UsageEventQueryPort events) {
        this.events = events;
    }

    @Override
    public List<UsageEvent> list(Instant since, Integer limit) {
        return events.findRecent(since, MonitoringLimit.resolve(limit));
    }
}
