package com.minte9.monitor.metrics.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.metrics.api.MetricResponse;
import com.minte9.monitor.metrics.domain.MetricRecord;
import com.minte9.monitor.metrics.service.MetricsIngestionService;

import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    
    private final MetricsIngestionService metricsIngestionService;

    public MetricsController(MetricsIngestionService metricsIngestionService) {
        this.metricsIngestionService = metricsIngestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricResponse ingest(@Valid @RequestBody MetricIngestRequest request) {
        MetricRecord saved = metricsIngestionService.ingest(request);
        return toResponse(saved);
    }

    @GetMapping
    public List<MetricResponse> findAll() {
        return metricsIngestionService.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/node/{nodeId}")
    public List<MetricResponse> findByNodeId(@PathVariable String nodeId) {
        return metricsIngestionService.findByNodeId(nodeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private MetricResponse toResponse(MetricRecord record) {
        return new MetricResponse(
            record.id(),
            record.nodeId(),
            record.metricType(),
            record.timestamp(),
            record.payload(),
            record.receivedAt()
        );
    }
}
