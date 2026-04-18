### Dashboard Service

Single-file procedural version.

~~~java
package com.minte9.monitor.dashboard;

import com.minte9.monitor.common.events.AlertTriggeredEvent;
import com.minte9.monitor.common.events.MetricReceivedEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@RestController
@RequestMapping("/api/dashboard")
public class DashboardServiceApplication {

    public static final String METRICS_EXCHANGE = "metrics.exchange";
    public static final String ALERTS_EXCHANGE = "alerts.exchange";

    public static final String DASHBOARD_METRICS_QUEUE = "dashboard.metrics.queue";
    public static final String DASHBOARD_ALERTS_QUEUE = "dashboard.alerts.queue";

    public static final String METRIC_RECEIVED_ROUTING_KEY = "metric.received";
    public static final String ALERT_TRIGGERED_ROUTING_KEY = "alert.triggered";

    private final Map<String, Map<String, MetricView>> latestMetricsByNode = new ConcurrentHashMap<>();
    private final Map<String, List<AlertView>> alertsByNode = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastUpdateByNode = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ContainerStatusView>> latestContainersByNode = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ServiceHealthView>> latestServicesByNode = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        SpringApplication.run(DashboardServiceApplication.class, args);
    }

    // ---------------------------------------------------------------------
    // RabbitMQ config
    // ---------------------------------------------------------------------

    @Bean
    public DirectExchange metricsExchange() {
        return new DirectExchange(METRICS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange alertsExchange() {
        return new DirectExchange(ALERTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue dashboardMetricsQueue() {
        return QueueBuilder.durable(DASHBOARD_METRICS_QUEUE).build();
    }

    @Bean
    public Queue dashboardAlertsQueue() {
        return QueueBuilder.durable(DASHBOARD_ALERTS_QUEUE).build();
    }

    @Bean
    public Binding dashboardMetricsBinding(Queue dashboardMetricsQueue, DirectExchange metricsExchange) {
        return BindingBuilder
            .bind(dashboardMetricsQueue)
            .to(metricsExchange)
            .with(METRIC_RECEIVED_ROUTING_KEY);
    }

    @Bean
    public Binding dashboardAlertBinding(Queue dashboardAlertsQueue, DirectExchange alertsExchange) {
        return BindingBuilder
            .bind(dashboardAlertsQueue)
            .to(alertsExchange)
            .with(ALERT_TRIGGERED_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---------------------------------------------------------------------
    // Event listeners
    // ---------------------------------------------------------------------

    @RabbitListener(queues = DASHBOARD_METRICS_QUEUE)
    public void onMetricReceived(MetricReceivedEvent event) {
        applyMetricEvent(event);
    }

    @RabbitListener(queues = DASHBOARD_ALERTS_QUEUE)
    public void onAlertTriggered(AlertTriggeredEvent event) {
        applyAlertEvent(event);
    }

    // ---------------------------------------------------------------------
    // REST endpoints
    // ---------------------------------------------------------------------

    @GetMapping("/nodes")
    public List<NodeDashboardView> findAllNodes() {
        return latestMetricsByNode.keySet().stream()
            .sorted()
            .map(this::buildNodeView)
            .toList();
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<NodeDashboardView> findNode(@PathVariable String nodeId) {
        if (!latestMetricsByNode.containsKey(nodeId)
                && !alertsByNode.containsKey(nodeId)
                && !latestContainersByNode.containsKey(nodeId)
                && !latestServicesByNode.containsKey(nodeId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(buildNodeView(nodeId));
    }

    @GetMapping("/nodes/{nodeId}/metrics")
    public Map<String, MetricView> findLatestMetricsByNode(@PathVariable String nodeId) {
        return latestMetricsByNode.getOrDefault(nodeId, Map.of());
    }

    @GetMapping("/nodes/{nodeId}/alerts")
    public List<AlertView> findAlertsByNode(@PathVariable String nodeId) {
        return alertsByNode.getOrDefault(nodeId, List.of());
    }

    @GetMapping("/nodes/{nodeId}/containers")
    public List<ContainerStatusView> findContainersByNode(@PathVariable String nodeId) {
        return latestContainersByNode.getOrDefault(nodeId, Map.of())
            .values()
            .stream()
            .sorted(Comparator.comparing(ContainerStatusView::containerName))
            .toList();
    }

    @GetMapping("/nodes/{nodeId}/services")
    public List<ServiceHealthView> findServicesByNode(@PathVariable String nodeId) {
        return latestServicesByNode.getOrDefault(nodeId, Map.of())
            .values()
            .stream()
            .sorted(Comparator.comparing(ServiceHealthView::serviceName))
            .toList();
    }

    // ---------------------------------------------------------------------
    // Projection functions
    // ---------------------------------------------------------------------

    private void applyMetricEvent(MetricReceivedEvent event) {
        MetricView metricView = new MetricView(
            event.metricType(),
            event.timestamp(),
            event.receivedAt(),
            event.payload()
        );

        latestMetricsByNode
            .computeIfAbsent(event.nodeId(), ignored -> new ConcurrentHashMap<>())
            .put(event.metricType(), metricView);

        lastUpdateByNode.put(event.nodeId(), Instant.now());

        switch (event.metricType()) {
            case "CONTAINER" -> projectContainer(event);
            case "SERVICE_HEALTH" -> projectService(event);
            default -> {
                // CPU / RAM / DISK stay only in latest metrics
            }
        }
    }

    private void applyAlertEvent(AlertTriggeredEvent event) {
        AlertView alertView = new AlertView(
            event.alertId(),
            event.metricType(),
            event.severity(),
            event.message(),
            event.triggeredAt(),
            event.details()
        );

        alertsByNode.computeIfAbsent(event.nodeId(), ignored -> new ArrayList<>()).add(alertView);

        List<AlertView> alerts = alertsByNode.get(event.nodeId());
        alerts.sort(Comparator.comparing(AlertView::triggeredAt).reversed());

        if (alerts.size() > 20) {
            alerts.subList(20, alerts.size()).clear();
        }

        lastUpdateByNode.put(event.nodeId(), Instant.now());
    }

    private void projectContainer(MetricReceivedEvent event) {
        String containerName = asString(event.payload().get("containerName"));
        String status = asString(event.payload().get("status"));
        String image = asString(event.payload().get("image"));

        if (containerName == null || containerName.isBlank()) {
            return;
        }

        ContainerStatusView view = new ContainerStatusView(
            containerName,
            status,
            image,
            event.timestamp(),
            event.receivedAt()
        );

        latestContainersByNode
            .computeIfAbsent(event.nodeId(), ignored -> new ConcurrentHashMap<>())
            .put(containerName, view);

        lastUpdateByNode.put(event.nodeId(), Instant.now());
    }

    private void projectService(MetricReceivedEvent event) {
        String serviceName = asString(event.payload().get("serviceName"));
        String status = asString(event.payload().get("status"));
        String url = asString(event.payload().get("url"));
        Integer httpStatus = asInteger(event.payload().get("httpStatus"));

        if (serviceName == null || serviceName.isBlank()) {
            return;
        }

        ServiceHealthView view = new ServiceHealthView(
            serviceName,
            status,
            url,
            httpStatus,
            event.timestamp(),
            event.receivedAt()
        );

        latestServicesByNode
            .computeIfAbsent(event.nodeId(), ignored -> new ConcurrentHashMap<>())
            .put(serviceName, view);

        lastUpdateByNode.put(event.nodeId(), Instant.now());
    }

    private NodeDashboardView buildNodeView(String nodeId) {
        return new NodeDashboardView(
            nodeId,
            latestMetricsByNode.getOrDefault(nodeId, Map.of()),
            alertsByNode.getOrDefault(nodeId, List.of()),
            latestContainersByNode.getOrDefault(nodeId, Map.of()).values().stream()
                .sorted(Comparator.comparing(ContainerStatusView::containerName))
                .toList(),
            latestServicesByNode.getOrDefault(nodeId, Map.of()).values().stream()
                .sorted(Comparator.comparing(ServiceHealthView::serviceName))
                .toList(),
            lastUpdateByNode.get(nodeId)
        );
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // View models
    // ---------------------------------------------------------------------

    public record MetricView(
        String metricType,
        Instant timestamp,
        Instant receivedAt,
        Map<String, Object> payload
    ) {}

    public record AlertView(
        String alertId,
        String metricType,
        String severity,
        String message,
        Instant triggeredAt,
        Map<String, Object> details
    ) {}

    public record ContainerStatusView(
        String containerName,
        String status,
        String image,
        Instant timestamp,
        Instant receivedAt
    ) {}

    public record ServiceHealthView(
        String serviceName,
        String status,
        String url,
        Integer httpStatus,
        Instant timestamp,
        Instant receivedAt
    ) {}

    public record NodeDashboardView(
        String nodeId,
        Map<String, MetricView> latestMetrics,
        List<AlertView> alerts,
        List<ContainerStatusView> containers,
        List<ServiceHealthView> services,
        Instant lastUpdatedAt
    ) {}
}
~~~