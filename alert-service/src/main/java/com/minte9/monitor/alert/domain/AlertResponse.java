/**
 * This DTO is returned by the REST controller.
 * 
 * It mirrors AlertRecord for now, but keeping a separate response DTO
 * is a good habit because API contracts often evolve independently.
 */

package com.minte9.monitor.alert.domain;

import java.time.Instant;
import java.util.Map;

import com.minte9.monitor.alert.domain.AlertSeverity;
import com.minte9.monitor.alert.domain.AlertStatus;

public record AlertResponse(
    String id,
    String metricId,
    String nodeId,
    String metricType,
    String message,
    AlertSeverity severity,
    AlertStatus status,
    Instant metricTimestamp,
    Instant triggeredAt,
    Map<String, Object> payload
) {
}
