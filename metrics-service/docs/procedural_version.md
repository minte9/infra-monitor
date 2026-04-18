### Metrics Service

Single-file procedural version.

~~~java
package com.minte9.monitor.metrics;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.common.api.MetricType;
import com.minte9.monitor.common.events.MetricReceivedEvent;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
public class MetricsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetricsServiceApplication.class, args);
    }

    @RestController
    @RequestMapping("/api/metrics")
    static class MetricsEndpoints {

        private static final Logger log = LoggerFactory.getLogger(MetricsEndpoints.class);

        private final MetricMongoRepository metrics;
        private final RabbitTemplate rabbit;

        MetricsEndpoints(MetricMongoRepository metrics, RabbitTemplate rabbit) {
            this.metrics = metrics;
            this.rabbit = rabbit;
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public Map<String, Object> ingest(@Valid @RequestBody MetricIngestRequest request) {

            log.info("Received metric request: {}", request);

            MetricRecord record = new MetricRecord(
                null,
                request.nodeId(),
                request.metricType(),
                request.timestamp(),
                request.payload(),
                Instant.now()
            );

            MetricRecord saved = metrics.save(record);

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

            return toMap(saved);
        }

        @GetMapping
        public List<Map<String, Object>> findAll() {
            return metrics.findAll().stream()
                .map(this::toMap)
                .toList();
        }

        @GetMapping("/node/{nodeId}")
        public List<Map<String, Object>> findByNodeId(@PathVariable String nodeId) {
            return metrics.findByNodeId(nodeId).stream()
                .map(this::toMap)
                .toList();
        }

        @GetMapping("/node/{nodeId}/latest")
        public Map<String, Object> findLatestByNodeId(@PathVariable String nodeId) {
            MetricRecord record = metrics.findFirstByNodeIdOrderByTimestampDesc(nodeId)
                .orElseThrow(() -> new MetricNotFoundException("No metrics found for node: " + nodeId));

            return toMap(record);
        }

        @GetMapping("/node/{nodeId}/latest/{metricType}")
        public Map<String, Object> findLatestByNodeIdAndMetricType(
            @PathVariable String nodeId,
            @PathVariable MetricType metricType
        ) {
            MetricRecord record = metrics
                .findFirstByNodeIdAndMetricTypeOrderByTimestampDesc(nodeId, metricType)
                .orElseThrow(() ->
                    new MetricNotFoundException(
                        "No metrics found for node " + nodeId + " and type " + metricType
                    )
                );

            return toMap(record);
        }

        private Map<String, Object> toMap(MetricRecord record) {
            return Map.of(
                "id", record.id(),
                "nodeId", record.nodeId(),
                "metricType", record.metricType(),
                "timestamp", record.timestamp(),
                "payload", record.payload(),
                "receivedAt", record.receivedAt()
            );
        }
    }

    @Document(collection = "metrics")
    record MetricRecord(
        @Id String id,
        @Indexed String nodeId,
        MetricType metricType,
        Instant timestamp,
        Map<String, Object> payload,
        Instant receivedAt
    ) {}

    interface MetricMongoRepository extends MongoRepository<MetricRecord, String> {
        List<MetricRecord> findByNodeId(String nodeId);

        List<MetricRecord> findByNodeIdAndMetricType(String nodeId, MetricType metricType);

        Optional<MetricRecord> findFirstByNodeIdOrderByTimestampDesc(String nodeId);

        Optional<MetricRecord> findFirstByNodeIdAndMetricTypeOrderByTimestampDesc(
            String nodeId,
            MetricType metricType
        );
    }

    static class MetricNotFoundException extends RuntimeException {
        MetricNotFoundException(String message) {
            super(message);
        }
    }

    @Configuration
    static class RabbitMqConfig {

        static final String METRICS_EXCHANGE = "metrics.exchange";
        static final String ALERT_QUEUE = "metrics.alert.queue";
        static final String METRIC_RECEIVED_ROUTING_KEY = "metric.received";

        @Bean
        Queue alertQueue() {
            return QueueBuilder.durable(ALERT_QUEUE).build();
        }

        @Bean
        DirectExchange metricsExchange() {
            return new DirectExchange(METRICS_EXCHANGE, true, false);
        }

        @Bean
        Binding metricReceivedBinding(Queue alertQueue, DirectExchange metricsExchange) {
            return BindingBuilder
                .bind(alertQueue)
                .to(metricsExchange)
                .with(METRIC_RECEIVED_ROUTING_KEY);
        }

        @Bean
        Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
            return new Jackson2JsonMessageConverter();
        }
    }

    @RestControllerAdvice
    static class ErrorHandler {

        @ExceptionHandler(MetricNotFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        Map<String, Object> handleMetricNotFound(MetricNotFoundException ex) {
            return Map.of(
                "error", "NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
            );
        }
    }
}
~~~