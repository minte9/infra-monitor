/**
 * Main projection returned by dashboard-service.
 * 
 * This aggregates:
 *  - latest metrics by type
 *  - recent alerts
 *  - last updated timestamp
 * 
 * It is designed for dashboard reads, not for writes.
 */
package com.minte9.monitor.dashboard.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NodeDashboardView(
    String nodeId,
    Map<String, MetricView> latestMetrics,
    List<AlertView> recentAlerts,
    Instant lastUpdateAt
) {

}
