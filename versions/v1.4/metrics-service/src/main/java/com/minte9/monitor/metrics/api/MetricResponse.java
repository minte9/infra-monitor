/**
 * Response DTO
 * 
 * We should not expose internal storage objects
 * directly from the controller.
 */
package com.minte9.monitor.metrics.api;

import java.time.Instant;
import java.util.Map;

import com.minte9.monitor.common.api.MetricType;

public record MetricResponse(
    String id,
    String nodeId,
    MetricType metricType,
    Instant timestamp,
    Map<String, Object> payload,
    Instant receiveAt
) {}
