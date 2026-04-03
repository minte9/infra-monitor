package com.minte9.monitor.common.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.Map;

public record MetricIngestRequest(
        @NotBlank String nodeId,
        @NotNull MetricType metricType,
        @NotNull Instant timestamp,
        @NotEmpty Map<String, Object> payload
) {}