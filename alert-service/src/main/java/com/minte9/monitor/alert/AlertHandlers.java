package com.minte9.monitor.alert;

import com.minte9.monitor.alert.config.RabbitMqConfig;
import com.minte9.monitor.alert.domain.AlertRecord;
import com.minte9.monitor.alert.domain.AlertSeverity;
import com.minte9.monitor.alert.domain.AlertStatus;
import com.minte9.monitor.alert.domain.AlertResponse;
import com.minte9.monitor.common.events.AlertTriggeredEvent;
import com.minte9.monitor.common.events.MetricReceivedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AlertHandlers {
    
    private static final Logger log = LoggerFactory.getLogger(AlertHandlers.class);

    private final RabbitTemplate rabbit;
    private final List<AlertRecord> storage = new CopyOnWriteArrayList<>();

    public AlertHandlers(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    // ========================
    // RabbitMQ consumer
    // ========================

    @RabbitListener(queues = RabbitMqConfig.ALERT_QUEUE)
    public void onMetricReceived(MetricReceivedEvent event) {

        log.info("Received metric event: metricId={}, nodeId={}, metricType={}",
                    event.metricId(), 
                    event.nodeId(), 
                    event.metricType());

        Optional<AlertRecord> alert = evaluateAndStore(event, storage);

        if (alert.isPresent()) {
            log.warn("ALERT TRIGGERED: id={}, nodeId={}, metricType={}, message={}",
                alert.get().id(),
                alert.get().nodeId(),
                alert.get().metricType(),
                alert.get().message()
            );
            
            publishAlertTriggered(rabbit, alert.get());

        } else {
            log.info("No alert triggered for metricId={}", event.metricId());
        }
    }

    // ===========================
    // Procedural business logic
    // ===========================

    public Optional<AlertRecord> evaluateAndStore(
            MetricReceivedEvent event, List<AlertRecord> storage) {

        String message = buildAlertMessage(event);

        if (message == null) {
            return Optional.empty();
        }

        AlertSeverity severity = determineSeverity(event);

        AlertRecord alertRecord = new AlertRecord(
                UUID.randomUUID().toString(),
                event.metricId(),
                event.nodeId(),
                event.metricType(),
                message,
                severity,
                AlertStatus.OPEN,
                event.timestamp(),
                Instant.now(),
                event.payload()
        );

        storage.add(alertRecord);
        return Optional.of(alertRecord);
    }

    private String buildAlertMessage(MetricReceivedEvent event) {
        Map<String, Object> payload = event.payload();
        String metricType = event.metricType();
        Instant timestamp = event.timestamp();

        return switch (metricType) {
            case "CPU" -> cpuAlertMessage(event.nodeId(), payload, timestamp);
            case "RAM" -> ramAlertMessage(event.nodeId(), payload, timestamp);
            case "DISK" -> diskAlertMessage(event.nodeId(), payload, timestamp);
            case "CONTAINER" -> containerAlertMessage(event.nodeId(), payload, timestamp);
            case "SERVICE" -> serviceAlertMessage(event.nodeId(), payload, timestamp);
            default -> null;
        };
    }

    public static void publishAlertTriggered(RabbitTemplate rabbit, AlertRecord alert) {
        AlertTriggeredEvent event = new AlertTriggeredEvent(
            alert.id(),
            alert.nodeId(),
            alert.metricType(),
            alert.severity().name(),
            alert.message(),
            alert.triggeredAt(),
            alert.payload()
        );

        rabbit.convertAndSend(
            RabbitMqConfig.ALERTS_EXCHANGE,
            RabbitMqConfig.ALERT_TRIGGERED_ROUTING_KEY,
            event,
            message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }
        );
    }

    public static AlertSeverity determineSeverity(MetricReceivedEvent event) {
        String metricType = event.metricType();
        Map<String, Object> payload = event.payload();

        return switch (metricType) {
            case "CPU" -> {
                Double value = percent(payload, "usagePercent");
                yield value != null && value >= 90 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            }
            case "RAM" -> {
                Double value = percent(payload, "usagePercent");
                yield value != null && value >= 95 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            }
            case "DISK" -> {
                Double value = percent(payload, "usagePercent");
                yield value != null && value >= 95 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            }
            case "CONTAINER", "SERVICE" -> AlertSeverity.CRITICAL;
            default -> AlertSeverity.INFO;
        };
    }

    public static String cpuAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 80.0) {
            return withTime(timestamp, 
                "High CPU usage on node %s: %.2f%%".formatted(nodeId, usagePercent)
            );
        }
        return null;
    }

    public static String ramAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 85.0) {
            return withTime(timestamp, 
                "High RAM usage on node %s: %.2f%%".formatted(nodeId, usagePercent)
            );
        }
        return null;
    }

    public static String diskAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 90.0) {
            return withTime(timestamp, 
                "High disk usage on node %s: %.2f%%".formatted(nodeId, usagePercent)
            );
        }
        return null;
    }

    public static String containerAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Object rawStatus = payload.get("status");
        Object containerName = payload.getOrDefault("containerName", "unknown-container");

        if (rawStatus == null) {
            return withTime(timestamp,
                "Container %s on node %s has unknown status".formatted(containerName, nodeId)
            );
        }

        String status = rawStatus.toString().trim().toLowerCase();

        boolean isCritical =
            status.startsWith("restarting") ||
            status.startsWith("exited") ||
            status.startsWith("dead") ||
            status.contains("unhealthy");

        if (isCritical) {
            return withTime(timestamp, 
                "Container %s on node %s is %s".formatted(containerName, nodeId, rawStatus)
            );
        }

        return null;
    }

    public static String serviceAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Object status = payload.get("status");
        Object serviceName = payload.getOrDefault("serviceName", "unknown-service");

        if (status != null && !"UP".equalsIgnoreCase(status.toString())) {
            return withTime(timestamp, 
                "Service %s on node %s is %s".formatted(serviceName, nodeId, status)
            );
        }

        return null;
    }

    public static Double percent(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    public static AlertResponse toResponse(AlertRecord alertRecord) {
        return new AlertResponse(
            alertRecord.id(),
            alertRecord.metricId(),
            alertRecord.nodeId(),
            alertRecord.metricType(),
            alertRecord.message(),
            alertRecord.severity(),
            alertRecord.status(),
            alertRecord.metricTimestamp(),
            alertRecord.triggeredAt(),
            alertRecord.payload()
        );
    }

    public static List<AlertRecord> copyStorage(List<AlertRecord> storage) {
        return new ArrayList<>(storage);
    }

    public static String withTime(Instant timestamp, String message) {
        return message + " / " + timestamp;
    }

    // ============================
    // Query methods for controller
    // ============================

    public List<AlertResponse> findAll() {
        return storage.stream()
            .map(AlertHandlers::toResponse)
            .toList();
    }

    public List<AlertResponse> findByNodeId(String nodeId) {
        return storage.stream()
            .filter(alert -> alert.nodeId().equals(nodeId))
            .map(AlertHandlers::toResponse)
            .toList();
    }

    public List<AlertResponse> findByStatus(String status) {
        return storage.stream()
            .filter(alert -> alert.status().name().equalsIgnoreCase(status))
            .map(AlertHandlers::toResponse)
            .toList();
    }
    
}
