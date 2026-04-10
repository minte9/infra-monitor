package com.minte9.monitor.dashboard.domain;

import java.time.Instant;

public record ContainerStatusView(
    String containerName,
    String status,
    String image,
    Instant timestamp,
    Instant receiveAt    
) {    
}
