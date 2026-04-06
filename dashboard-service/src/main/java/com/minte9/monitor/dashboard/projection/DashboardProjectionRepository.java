/**
 * In-memory projection repository.
 * 
 * This class owns the read models used by dashboard-service. 
 * Since multiple RabbitMQ listener threads may update projections, 
 * use thread-safe structures.
 */

package com.minte9.monitor.dashboard.projection;

import com.minte9.monitor.dashboard.domain.AlertView;
import com.minte9.monitor.dashboard.domain.MetricView;
import com.minte9.monitor.dashboard.domain.NodeDashboardView;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardProjectionRepository {
    
    private final Map<String, Map<String, MetricView>> latestMetricsByNode = new ConcurrentHashMap<>();
    private final Map<String, List<AlertView>> alertsByNode = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastUpdateByNode = new ConcurrentHashMap<>();

    public void updateMetric(String nodeId, MetricView metricView) {
        latestMetricsByNode
            .computeIfAbsent(nodeId, ignored -> new ConcurrentHashMap<>())
            .put(metricView.metricType(), metricView);
        
        lastUpdateByNode.put(nodeId, Instant.now());
    }

    public void addAlert(String nodeId, AlertView alertView) {
        alertsByNode.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(alertView);

        // keep newest alerts first
        alertsByNode.get(nodeId).sort(Comparator.comparing(AlertView::triggerAt).reversed());

        // optional: keep only the last 20 alerts per node
        List<AlertView> current = alertsByNode.get(nodeId);
        if (current.size() > 20) {
            current.subList(20, current.size()).clear();
        }

        lastUpdateByNode.put(nodeId, Instant.now());
    }

    public List<NodeDashboardView> findAllNodes() {
        return latestMetricsByNode.keySet().stream()
                .sorted()
                .map(this::findNode)
                .toList();
    }

    public NodeDashboardView findNode(String nodeId) {
        Map<String, MetricView> latestMetrics = latestMetricsByNode.getOrDefault(nodeId, Map.of());
        List<AlertView> alerts = alertsByNode.getOrDefault(nodeId, List.of());
        Instant lastUpdatedAt = lastUpdateByNode.get(nodeId);

        return new NodeDashboardView(nodeId, latestMetrics, alerts, lastUpdatedAt);
    }

    public Map<String, MetricView> findLatestMetricsByNode(String nodeId) {
        return latestMetricsByNode.getOrDefault(nodeId, Map.of());
    }

    public List<AlertView> findAlertsByNode(String nodeId) {
        return alertsByNode.getOrDefault(nodeId, List.of());
    }

}
