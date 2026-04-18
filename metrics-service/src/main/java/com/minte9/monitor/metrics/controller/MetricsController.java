package com.minte9.monitor.metrics.controller;

import com.minte9.monitor.common.api.MetricType;
import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.common.events.MetricReceivedEvent;

import com.minte9.monitor.metrics.domain.MetricRecord;
import com.minte9.monitor.metrics.domain.MetricResponse;
import com.minte9.monitor.metrics.repository.MetricMongoRepository;
import com.minte9.monitor.metrics.config.RabbitMqConfig;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    
    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);

    private final MetricMongoRepository metrics;
    private final RabbitTemplate rabbit;

    // Controller
    public MetricsController(MetricMongoRepository metrics, RabbitTemplate rabbit) {
        this.metrics = metrics;
        this.rabbit = rabbit;
    }

    // Endpoints
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricResponse ingest(@Valid @RequestBody MetricIngestRequest request) {

        log.info("Received metric request: {}", request);

        // Save record (repository)
        MetricRecord metricRecord = new MetricRecord(
            null,
            request.nodeId(),
            request.metricType(),
            request.timestamp(),
            request.payload(),
            Instant.now()
        );
        MetricRecord saved = metrics.save(metricRecord);

        // Publish event (rabbit)
        MetricReceivedEvent event = new MetricReceivedEvent(
            saved.id(),
            saved.nodeId(),
            saved.metricType().name(),
            saved.timestamp(),
            saved.receivedAt(),
            saved.payload()
        );

        rabbit.convertAndSend(
            RabbitMqConfig.METRICS_EXCHANGE,
            RabbitMqConfig.METRIC_RECEIVED_ROUTING_KEY,
            event,
            message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }
        );
        
        return this.toResponse(saved);
    }

    // Endpoint (default: /api/metrics)
    @GetMapping
    public List<MetricResponse> findAll() {
        return metrics.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/node/{nodeId}")
    public List<MetricResponse> findByNodeId(@PathVariable String nodeId) {
        return metrics.findByNodeId(nodeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/node/{nodeId}/latest")
    public MetricResponse findLatestByNodeId(@PathVariable String nodeId) {
        MetricRecord record = metrics.findFirstByNodeIdOrderByTimestampDesc(nodeId)
                .orElseThrow(() -> 
                    new MetricNotFoundException("No metrics found for node: " + nodeId));
        return toResponse(record);
    }

    @GetMapping("/node/{nodeId}/latest/{metricType}")
    public MetricResponse findLatestByNodeIdAndMetricType(
        @PathVariable String nodeId,
        @PathVariable MetricType metricType
    ) {
        MetricRecord record = metrics
                .findFirstByNodeIdAndMetricTypeOrderByTimestampDesc(nodeId, metricType)
                .orElseThrow(() -> 
                    new MetricNotFoundException("No metrics found for node " + nodeId + " and type " + metricType));
        return toResponse(record);
    }

    // Utills
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
