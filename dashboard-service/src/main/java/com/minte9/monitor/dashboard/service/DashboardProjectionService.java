/**
 * Application service that updates dashboard projections from incoming events. 
 * 
 * Keep listeners thin.
 * Let this service contain the projection update logic.
 */

package com.minte9.monitor.dashboard.service;

import com.minte9.monitor.common.events.AlertTriggeredEvent;
import com.minte9.monitor.common.events.MetricReceivedEvent;
import com.minte9.monitor.dashboard.domain.AlertView;
import com.minte9.monitor.dashboard.domain.MetricView;
import com.minte9.monitor.dashboard.domain.NodeDashboardView;
import com.minte9.monitor.dashboard.projection.DashboardProjectionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DashboardProjectionService {
    
    private final DashboardProjectionRepository repository;

    public DashboardProjectionService(DashboardProjectionRepository repository) {
        this.repository = repository;
    }

    public void applyMetricEvent(MetricReceivedEvent event) {
        MetricView metricView = new MetricView(
            event.metricType(),
            event.timestamp(),
            event.receiveAt(),
            event.payload()
        );

        repository.updateMetric(event.nodeId(), metricView);
    }

    public void applyAlertEvent(AlertTriggeredEvent event) {
        AlertView alertView = new AlertView(
                event.alertId(),
                event.metricType(),
                event.severity(),
                event.message(),
                event.triggeredAt(),
                event.details()
        );

        repository.addAlert(event.nodeId(), alertView);
    }

    public List<NodeDashboardView> findAllNodes() {
        return repository.findAllNodes();
    }

    public NodeDashboardView findNode(String nodeId) {
        return repository.findNode(nodeId);
    }

    public Map<String, MetricView> findLatestMetricsByNode(String nodeId) {
        return repository.findLatestMetricsByNode(nodeId);
    }

    public List<AlertView> findAlertsByNode(String nodeId) {
        return repository.findAlertsByNode(nodeId);
    }

}
