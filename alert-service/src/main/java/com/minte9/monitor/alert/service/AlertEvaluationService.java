/**
 * This service contains the alert decision logic.
 * 
 * Input:
 *  MetricReceivedEvent from RabbitMQ
 * 
 * Output:
 *  optional AlertRecord
 * 
 * If no alert condition matches, nothing is stored. 
 * If a rule matches, a new OPEN alert is created and saved.
 */

package com.minte9.monitor.alert.service;

import com.minte9.monitor.alert.domain.AlertRecord;
import com.minte9.monitor.alert.domain.AlertSeverity;
import com.minte9.monitor.alert.domain.AlertStatus;
import com.minte9.monitor.alert.repository.AlertRepository;
import com.minte9.monitor.common.events.MetricReceivedEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlertEvaluationService {
    
    private final AlertRepository alertRepository;

    public AlertEvaluationService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    // Event evaluation

    public Optional<AlertRecord> evaluate(MetricReceivedEvent event) {
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

        return Optional.of(alertRepository.save(alertRecord));
    }

    // Alert messages

    private String buildAlertMessage(MetricReceivedEvent event) {
        Map<String, Object> payload = event.payload();
        String metricType = event.metricType();

        return switch (metricType) {
            case "CPU" -> cpuAlertMessage(event.nodeId(), payload);
            case "RAM" -> ramAlertMessage(event.nodeId(), payload);
            case "DISK" -> diskAlertMessage(event.nodeId(), payload);
            case "CONTAINER" -> containerAlertMessage(event.nodeId(), payload);
            case "SERVICE" -> serviceAlertMessage(event.nodeId(), payload);
            default -> null;
        };
    }

    // Messages cases

    private String cpuAlertMessage(String nodeId, Map<String, Object> payload) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 80.0) {
            return "High CPU usage on node %s: %.2f%%".formatted(nodeId, usagePercent);
        }
        return null;
    }

    private String ramAlertMessage(String nodeId, Map<String, Object> payload) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 85.0) {
            return "High RAM usage on node %s: %.2f%%".formatted(nodeId, usagePercent);
        }
        return null;
    }

    private String diskAlertMessage(String nodeId, Map<String, Object> payload) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 90.0) {
            return "High disk usage on node %s: %.2f%%".formatted(nodeId, usagePercent);
        }
        return null;
    }

    private String containerAlertMessage(String nodeId, Map<String, Object> payload) {
        Object status = payload.get("status");
        Object containerName = payload.getOrDefault("containerName", "unknown-container");

        if (status != null && !"UP".equalsIgnoreCase(status.toString())) {
            return "Container %s on node %s is %s".formatted(containerName, nodeId, status);
        }
        return null;
    }

    private String serviceAlertMessage(String nodeId, Map<String, Object> payload) {
        Object status = payload.get("status");
        Object serviceName = payload.getOrDefault("serviceName", "unknown-service");

        if (status != null && !"UP".equalsIgnoreCase(status.toString())) {
            return "Service %s on node %s is %s".formatted(serviceName, nodeId, status);
        }
        return null;
    }

    // Percent

    private Double percent(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    // Severity

    private AlertSeverity determineSeverity(MetricReceivedEvent event) {
        String metricType = event.metricType();
        Map<String, Object> payload = event.payload();

        return switch (metricType) {
            case "CPU" -> percent(payload, "usagePercent") >= 90 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            case "RAM" -> percent(payload, "usagePercent") >= 95 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            case "DISK" -> percent(payload, "usagePercent") >= 95 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            case "CONTAINER", "SERVICE" -> AlertSeverity.CRITICAL;
            default -> AlertSeverity.INFO;
        };
    }

    // Repository find methods

    public List<AlertRecord> findAll() {
        return alertRepository.findAll();
    }

    public List<AlertRecord> findByNodeId(String nodeId) {
        return alertRepository.findByNodeId(nodeId);
    }

    public List<AlertRecord> findByStatus(String status) {
        return alertRepository.findByStatus(status);
    }
}
