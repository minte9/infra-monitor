package com.minte9.monitor.dashboard.domain;

import java.time.Instant;

public record ServiceHealthView (
    String serviceName,
    String status,
    String url,
    Integer httpStatus,
    Instant timestamp,
    Instant receiveAt
) {    
}
