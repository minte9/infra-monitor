package com.minte9.monitor.common.events;

import java.time.Instant;
import java.util.Map;

public record MetricReceivedEvent(
        String eventId,
        String nodeId,
        String metricType,
        Instant timestamp,
        Map<String, Object> payload
) {}