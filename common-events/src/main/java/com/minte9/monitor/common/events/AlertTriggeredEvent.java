/**
 * Shared event contract published by alert-service and 
 * consumed by dashboard-service.
 * 
 * Keep event contracts simple and transport-focus.
 * Do not put persistence annotations here.
 * Do not couple this class to MongoDB or REST controller classes. 
 */

package com.minte9.monitor.common.events;

import java.time.Instant;
import java.util.Map;

public record AlertTriggeredEvent(
    String alertId,
    String nodeId,
    String metricType,
    String severity,
    String message,
    Instant triggeredAt,
    Map<String, Object> details
) {    
}
