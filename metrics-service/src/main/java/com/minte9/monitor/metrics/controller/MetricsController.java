package com.minte9.monitor.metrics.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.metrics.api.MetricResponse;
import com.minte9.monitor.common.api.MetricType;
import com.minte9.monitor.metrics.domain.MetricRecord;
import com.minte9.monitor.metrics.service.MetricsIngestionService;

import java.util.List;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    
    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);
    private final MetricsIngestionService metricsIngestionService;

    public MetricsController(MetricsIngestionService metricsIngestionService) {
        this.metricsIngestionService = metricsIngestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricResponse ingest(@Valid @RequestBody MetricIngestRequest request) {
        log.info("Received metric request: {}", request);
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

    @GetMapping("/node/{nodeId}/latest")
    public MetricResponse findLatestByNodeId(@PathVariable String nodeId) {
        MetricRecord record = metricsIngestionService.findLatestByNodeId(nodeId)
                .orElseThrow(() -> new MetricNotFoundException("No metrics found for node: " + nodeId));
        return toResponse(record);
    }

    @GetMapping("/node/{nodeId}/latest/{metricType}")
    public MetricResponse findLatestByNodeIdAndMetricType(@PathVariable String nodeId,
                                                          @PathVariable MetricType metricType) {
        MetricRecord record = metricsIngestionService
                .findLatestByNodeIdAndMetricType(nodeId, metricType)
                .orElseThrow(() -> new MetricNotFoundException(
                        "No metrics found for node " + nodeId + " and type " + metricType));

        return toResponse(record);
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
