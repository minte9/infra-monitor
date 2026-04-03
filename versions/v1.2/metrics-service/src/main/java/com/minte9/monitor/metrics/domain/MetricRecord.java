/**
 * Internal domain model
 */
package com.minte9.monitor.metrics.domain;

import java.time.Instant;
import java.util.Map;

import com.minte9.monitor.common.api.MetricType;

public record MetricRecord(
    String id,
    String nodeId,
    MetricType metricType,
    Instant timestamp,
    Map<String, Object> payload,
    Instant receivedAt
) {}
