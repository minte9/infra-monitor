/**
 * Dashboard-friendly representation of an alert. 
 * 
 * This is a projection object, not necessary the same
 * as the alert-service domain model.
 */
package com.minte9.monitor.dashboard.domain;

import java.time.Instant;
import java.util.Map;

public record AlertView(
    String alertId,
    String metricType,
    String severity,
    String message,
    Instant triggerAt,
    Map<String, Object> details
) {    
}
