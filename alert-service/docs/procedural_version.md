### Dashboard Service

Single-file procedural version.

~~~java
package com.minte9.monitor.allinone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableScheduling
@RestController
@RequestMapping("/api")
public class InfraMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfraMonitorApplication.class, args);
    }

    // =========================================================
    // In-memory stores
    // =========================================================

    private final List<MetricRecord> metricStore = new CopyOnWriteArrayList<>();
    private final List<AlertRecord> alertStore = new CopyOnWriteArrayList<>();
    private final Map<String, NodeDashboardView> dashboardStore = new ConcurrentHashMap<>();

    // =========================================================
    // Internal event bus (replaces RabbitMQ in this one-file demo)
    // =========================================================

    private final List<MetricReceivedEvent> metricEvents = new CopyOnWriteArrayList<>();
    private final List<AlertTriggeredEvent> alertEvents = new CopyOnWriteArrayList<>();

    // =========================================================
    // Config
    // =========================================================

    private static final String NODE_ID = "vps-01";
    private static final List<String> SERVICES = List.of(
        "metrics-service",
        "alert-service",
        "dashboard-service"
    );

    // =========================================================
    // NODE AGENT PART
    // =========================================================

    @Scheduled(fixedDelay = 15000)
    public void collectAndSendMetrics() {
        List<MetricIngestRequest> metrics = new ArrayList<>();

        metrics.add(cpuMetric());
        metrics.add(ramMetric());
        metrics.add(diskMetric());
        metrics.addAll(serviceHealthMetrics());
        metrics.addAll(containerMetrics());

        for (MetricIngestRequest metric : metrics) {
            ingestMetric(metric);
        }
    }

    public MetricIngestRequest cpuMetric() {
        OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        double cpu = osBean.getSystemCpuLoad();
        if (cpu < 0) cpu = 0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("usagePercent", cpu * 100.0);

        return new MetricIngestRequest(
            UUID.randomUUID().toString(),
            NODE_ID,
            "CPU",
            Instant.now(),
            payload
        );
    }

    public MetricIngestRequest ramMetric() {
        OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        long total = osBean.getTotalMemorySize();
        long free = osBean.getFreeMemorySize();
        long used = total - free;

        double usagePercent = total > 0 ? ((double) used / total) * 100.0 : 0.0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalBytes", total);
        payload.put("usedBytes", used);
        payload.put("freeBytes", free);
        payload.put("usagePercent", usagePercent);

        return new MetricIngestRequest(
            UUID.randomUUID().toString(),
            NODE_ID,
            "RAM",
            Instant.now(),
            payload
        );
    }

    public MetricIngestRequest diskMetric() {
        File root = new File("/");
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        long used = total - free;

        double usagePercent = total > 0 ? ((double) used / total) * 100.0 : 0.0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", "/");
        payload.put("totalBytes", total);
        payload.put("usedBytes", used);
        payload.put("freeBytes", free);
        payload.put("usagePercent", usagePercent);

        return new MetricIngestRequest(
            UUID.randomUUID().toString(),
            NODE_ID,
            "DISK",
            Instant.now(),
            payload
        );
    }

    public List<MetricIngestRequest> serviceHealthMetrics() {
        List<MetricIngestRequest> result = new ArrayList<>();

        for (String serviceName : SERVICES) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("serviceName", serviceName);
            payload.put("status", "UP"); // simplified demo

            result.add(new MetricIngestRequest(
                UUID.randomUUID().toString(),
                NODE_ID,
                "SERVICE",
                Instant.now(),
                payload
            ));
        }

        return result;
    }

    public List<MetricIngestRequest> containerMetrics() {
        List<MetricIngestRequest> result = new ArrayList<>();

        List<String> containers = List.of(
            "infra-monitor-metrics-service",
            "infra-monitor-alert-service",
            "infra-monitor-dashboard-service"
        );

        for (String name : containers) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("containerName", name);
            payload.put("status", "running"); // simplified demo

            result.add(new MetricIngestRequest(
                UUID.randomUUID().toString(),
                NODE_ID,
                "CONTAINER",
                Instant.now(),
                payload
            ));
        }

        return result;
    }

    // =========================================================
    // METRICS SERVICE PART
    // =========================================================

    @PostMapping("/metrics")
    @ResponseStatus(HttpStatus.CREATED)
    public MetricResponse ingestMetricHttp(@RequestBody MetricIngestRequest request) {
        MetricRecord saved = ingestMetric(request);
        return toMetricResponse(saved);
    }

    @GetMapping("/metrics")
    public List<MetricResponse> findAllMetrics() {
        return metricStore.stream()
            .map(this::toMetricResponse)
            .toList();
    }

    @GetMapping("/metrics/node/{nodeId}")
    public List<MetricResponse> findMetricsByNode(@PathVariable String nodeId) {
        return metricStore.stream()
            .filter(m -> m.nodeId().equals(nodeId))
            .map(this::toMetricResponse)
            .toList();
    }

    public MetricRecord ingestMetric(MetricIngestRequest request) {
        validateMetric(request);

        MetricRecord saved = new MetricRecord(
            request.metricId(),
            request.nodeId(),
            request.metricType(),
            request.timestamp(),
            request.payload(),
            Instant.now()
        );

        metricStore.add(saved);

        MetricReceivedEvent event = new MetricReceivedEvent(
            saved.id(),
            saved.nodeId(),
            saved.metricType(),
            saved.timestamp(),
            saved.payload()
        );

        publishMetricEvent(event);
        return saved;
    }

    public void validateMetric(MetricIngestRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        if (blank(request.metricId())) throw new BadRequestException("metricId is required");
        if (blank(request.nodeId())) throw new BadRequestException("nodeId is required");
        if (blank(request.metricType())) throw new BadRequestException("metricType is required");
        if (request.timestamp() == null) throw new BadRequestException("timestamp is required");
        if (request.payload() == null) throw new BadRequestException("payload is required");
    }

    public void publishMetricEvent(MetricReceivedEvent event) {
        metricEvents.add(event);

        handleAlertFromMetric(event);
        updateDashboardFromMetric(event);
    }

    // =========================================================
    // ALERT SERVICE PART
    // =========================================================

    @GetMapping("/alerts")
    public List<AlertResponse> findAllAlerts() {
        return alertStore.stream().map(this::toAlertResponse).toList();
    }

    @GetMapping("/alerts/node/{nodeId}")
    public List<AlertResponse> findAlertsByNode(@PathVariable String nodeId) {
        return alertStore.stream()
            .filter(a -> a.nodeId().equals(nodeId))
            .map(this::toAlertResponse)
            .toList();
    }

    @GetMapping("/alerts/status/{status}")
    public List<AlertResponse> findAlertsByStatus(@PathVariable String status) {
        return alertStore.stream()
            .filter(a -> a.status().name().equalsIgnoreCase(status))
            .map(this::toAlertResponse)
            .toList();
    }

    public void handleAlertFromMetric(MetricReceivedEvent event) {
        String message = buildAlertMessage(event);
        if (message == null) return;

        AlertSeverity severity = determineSeverity(event);

        AlertRecord alert = new AlertRecord(
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

        alertStore.add(alert);

        AlertTriggeredEvent alertEvent = new AlertTriggeredEvent(
            alert.id(),
            alert.nodeId(),
            alert.metricType(),
            alert.severity().name(),
            alert.message(),
            alert.triggeredAt(),
            alert.payload()
        );

        publishAlertEvent(alertEvent);
    }

    public void publishAlertEvent(AlertTriggeredEvent event) {
        alertEvents.add(event);
        updateDashboardFromAlert(event);
    }

    public String buildAlertMessage(MetricReceivedEvent event) {
        Map<String, Object> payload = event.payload();
        Instant timestamp = event.timestamp();

        return switch (event.metricType()) {
            case "CPU" -> cpuAlertMessage(event.nodeId(), payload, timestamp);
            case "RAM" -> ramAlertMessage(event.nodeId(), payload, timestamp);
            case "DISK" -> diskAlertMessage(event.nodeId(), payload, timestamp);
            case "CONTAINER" -> containerAlertMessage(event.nodeId(), payload, timestamp);
            case "SERVICE" -> serviceAlertMessage(event.nodeId(), payload, timestamp);
            default -> null;
        };
    }

    public AlertSeverity determineSeverity(MetricReceivedEvent event) {
        Map<String, Object> payload = event.payload();

        return switch (event.metricType()) {
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

    public String cpuAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 80.0) {
            return withTime(timestamp,
                "High CPU usage on node %s: %.2f%%".formatted(nodeId, usagePercent));
        }
        return null;
    }

    public String ramAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 85.0) {
            return withTime(timestamp,
                "High RAM usage on node %s: %.2f%%".formatted(nodeId, usagePercent));
        }
        return null;
    }

    public String diskAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 90.0) {
            return withTime(timestamp,
                "High disk usage on node %s: %.2f%%".formatted(nodeId, usagePercent));
        }
        return null;
    }

    public String containerAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Object rawStatus = payload.get("status");
        Object containerName = payload.getOrDefault("containerName", "unknown-container");

        if (rawStatus == null) {
            return withTime(timestamp,
                "Container %s on node %s has unknown status".formatted(containerName, nodeId));
        }

        String status = rawStatus.toString().trim().toLowerCase();

        boolean isCritical =
            status.startsWith("restarting") ||
            status.startsWith("exited") ||
            status.startsWith("dead") ||
            status.contains("unhealthy");

        if (isCritical) {
            return withTime(timestamp,
                "Container %s on node %s is %s".formatted(containerName, nodeId, rawStatus));
        }

        return null;
    }

    public String serviceAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Object status = payload.get("status");
        Object serviceName = payload.getOrDefault("serviceName", "unknown-service");

        if (status != null && !"UP".equalsIgnoreCase(status.toString())) {
            return withTime(timestamp,
                "Service %s on node %s is %s".formatted(serviceName, nodeId, status));
        }

        return null;
    }

    public String withTime(Instant timestamp, String message) {
        return "[" + timestamp + "] " + message;
    }

    public Double percent(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number n) return n.doubleValue();
        return null;
    }

    // =========================================================
    // DASHBOARD SERVICE PART
    // =========================================================

    @GetMapping("/dashboard")
    public List<NodeDashboardView> dashboardAll() {
        return dashboardStore.values().stream().toList();
    }

    @GetMapping("/dashboard/{nodeId}")
    public ResponseEntity<NodeDashboardView> dashboardByNode(@PathVariable String nodeId) {
        NodeDashboardView view = dashboardStore.get(nodeId);
        if (view == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(view);
    }

    public void updateDashboardFromMetric(MetricReceivedEvent event) {
        NodeDashboardView current = dashboardStore.getOrDefault(
            event.nodeId(),
            emptyDashboard(event.nodeId())
        );

        NodeDashboardView updated = switch (event.metricType()) {
            case "CPU" -> current.withCpu(percent(event.payload(), "usagePercent"), event.timestamp());
            case "RAM" -> current.withRam(percent(event.payload(), "usagePercent"), event.timestamp());
            case "DISK" -> current.withDisk(percent(event.payload(), "usagePercent"), event.timestamp());
            case "SERVICE" -> current.withService(
                string(event.payload(), "serviceName"),
                string(event.payload(), "status"),
                event.timestamp()
            );
            case "CONTAINER" -> current.withContainer(
                string(event.payload(), "containerName"),
                string(event.payload(), "status"),
                event.timestamp()
            );
            default -> current;
        };

        dashboardStore.put(event.nodeId(), updated);
    }

    public void updateDashboardFromAlert(AlertTriggeredEvent event) {
        NodeDashboardView current = dashboardStore.getOrDefault(
            event.nodeId(),
            emptyDashboard(event.nodeId())
        );

        List<AlertSummary> alerts = new ArrayList<>(current.activeAlerts());
        alerts.add(new AlertSummary(
            event.alertId(),
            event.metricType(),
            event.severity(),
            event.message(),
            event.timestamp()
        ));

        NodeDashboardView updated = current.withAlerts(alerts, event.timestamp());
        dashboardStore.put(event.nodeId(), updated);
    }

    public NodeDashboardView emptyDashboard(String nodeId) {
        return new NodeDashboardView(
            nodeId,
            null,
            null,
            null,
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new ArrayList<>(),
            null
        );
    }

    // =========================================================
    // Helpers
    // =========================================================

    public String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    public boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public MetricResponse toMetricResponse(MetricRecord m) {
        return new MetricResponse(
            m.id(),
            m.nodeId(),
            m.metricType(),
            m.timestamp(),
            m.payload(),
            m.receivedAt()
        );
    }

    public AlertResponse toAlertResponse(AlertRecord a) {
        return new AlertResponse(
            a.id(),
            a.metricId(),
            a.nodeId(),
            a.metricType(),
            a.message(),
            a.severity(),
            a.status(),
            a.metricTimestamp(),
            a.triggeredAt(),
            a.payload()
        );
    }

    // =========================================================
    // Exception handler
    // =========================================================

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", 400);
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    // =========================================================
    // Records / DTOs / enums
    // =========================================================

    public record MetricIngestRequest(
        String metricId,
        String nodeId,
        String metricType,
        Instant timestamp,
        Map<String, Object> payload
    ) {}

    public record MetricRecord(
        String id,
        String nodeId,
        String metricType,
        Instant timestamp,
        Map<String, Object> payload,
        Instant receivedAt
    ) {}

    public record MetricResponse(
        String id,
        String nodeId,
        String metricType,
        Instant timestamp,
        Map<String, Object> payload,
        Instant receivedAt
    ) {}

    public record MetricReceivedEvent(
        String metricId,
        String nodeId,
        String metricType,
        Instant timestamp,
        Map<String, Object> payload
    ) {}

    public record AlertTriggeredEvent(
        String alertId,
        String nodeId,
        String metricType,
        String severity,
        String message,
        Instant timestamp,
        Map<String, Object> payload
    ) {}

    public enum AlertSeverity {
        INFO, WARNING, CRITICAL
    }

    public enum AlertStatus {
        OPEN
    }

    public record AlertRecord(
        String id,
        String metricId,
        String nodeId,
        String metricType,
        String message,
        AlertSeverity severity,
        AlertStatus status,
        Instant metricTimestamp,
        Instant triggeredAt,
        Map<String, Object> payload
    ) {}

    public record AlertResponse(
        String id,
        String metricId,
        String nodeId,
        String metricType,
        String message,
        AlertSeverity severity,
        AlertStatus status,
        Instant metricTimestamp,
        Instant triggeredAt,
        Map<String, Object> payload
    ) {}

    public record AlertSummary(
        String id,
        String metricType,
        String severity,
        String message,
        Instant timestamp
    ) {}

    public record NodeDashboardView(
        String nodeId,
        Double cpuUsagePercent,
        Double ramUsagePercent,
        Double diskUsagePercent,
        Map<String, String> services,
        Map<String, String> containers,
        List<AlertSummary> activeAlerts,
        Instant updatedAt
    ) {
        public NodeDashboardView withCpu(Double cpu, Instant updatedAt) {
            return new NodeDashboardView(
                nodeId, cpu, ramUsagePercent, diskUsagePercent,
                services, containers, activeAlerts, updatedAt
            );
        }

        public NodeDashboardView withRam(Double ram, Instant updatedAt) {
            return new NodeDashboardView(
                nodeId, cpuUsagePercent, ram, diskUsagePercent,
                services, containers, activeAlerts, updatedAt
            );
        }

        public NodeDashboardView withDisk(Double disk, Instant updatedAt) {
            return new NodeDashboardView(
                nodeId, cpuUsagePercent, ramUsagePercent, disk,
                services, containers, activeAlerts, updatedAt
            );
        }

        public NodeDashboardView withService(String name, String status, Instant updatedAt) {
            Map<String, String> copy = new LinkedHashMap<>(services);
            if (name != null) copy.put(name, status);
            return new NodeDashboardView(
                nodeId, cpuUsagePercent, ramUsagePercent, diskUsagePercent,
                copy, containers, activeAlerts, updatedAt
            );
        }

        public NodeDashboardView withContainer(String name, String status, Instant updatedAt) {
            Map<String, String> copy = new LinkedHashMap<>(containers);
            if (name != null) copy.put(name, status);
            return new NodeDashboardView(
                nodeId, cpuUsagePercent, ramUsagePercent, diskUsagePercent,
                services, copy, activeAlerts, updatedAt
            );
        }

        public NodeDashboardView withAlerts(List<AlertSummary> alerts, Instant updatedAt) {
            return new NodeDashboardView(
                nodeId, cpuUsagePercent, ramUsagePercent, diskUsagePercent,
                services, containers, alerts, updatedAt
            );
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    // =========================================================
    // Optional demo endpoints
    // =========================================================

    @PostMapping("/demo/cpu/{percent}")
    public Map<String, Object> demoCpu(@PathVariable double percent) {
        MetricIngestRequest request = new MetricIngestRequest(
            UUID.randomUUID().toString(),
            NODE_ID,
            "CPU",
            Instant.now(),
            Map.of("usagePercent", percent)
        );
        ingestMetric(request);
        return Map.of("ok", true, "cpu", percent);
    }

    @PostMapping("/demo/service/{serviceName}/{status}")
    public Map<String, Object> demoService(
        @PathVariable String serviceName,
        @PathVariable String status
    ) {
        MetricIngestRequest request = new MetricIngestRequest(
            UUID.randomUUID().toString(),
            NODE_ID,
            "SERVICE",
            Instant.now(),
            Map.of("serviceName", serviceName, "status", status)
        );
        ingestMetric(request);
        return Map.of("ok", true, "service", serviceName, "status", status);
    }
}
~~~