/**
 * A generic dashboard view of the latest metric 
 * for a givent metric type.
 * 
 * Example:
 *  - latest CPU metric for node vps-01
 *  - latest RAM metric for nod vps-01 
 */
package com.minte9.monitor.dashboard.domain;

import java.time.Instant;
import java.util.Map;

public record MetricView(
    String metricType,
    Instant timestamp,
    Instant receiveAt,
    Map<String, Object> payload
) {
}
