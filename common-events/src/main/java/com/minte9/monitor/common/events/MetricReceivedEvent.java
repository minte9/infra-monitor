package com.minte9.monitor.common.events;

import java.time.Instant;
import java.util.Map;

public record MetricReceivedEvent(
        String metricId,
        String nodeId,
        String metricType,
        Instant timestamp,
        Instant receiveAt, 
        Map<String, Object> payload
) {}