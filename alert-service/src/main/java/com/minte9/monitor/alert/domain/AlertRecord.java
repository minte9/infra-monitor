/**
 * This is the internal alert model stored by alert-service.
 * 
 * It is intentionaly separate from:
 *  - RabbitMQ event contracts
 *  - REST DTOs
 * 
 * The separation makes refactoring easier later. 
 */

package com.minte9.monitor.alert.domain;

import java.time.Instant;
import java.util.Map;

public record AlertRecord(
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