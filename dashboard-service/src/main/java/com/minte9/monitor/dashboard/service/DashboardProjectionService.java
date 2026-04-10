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
import com.minte9.monitor.dashboard.domain.ContainerStatusView;
import com.minte9.monitor.dashboard.domain.ServiceHealthView;
import com.minte9.monitor.dashboard.domain.ContainerStatusView;
import com.minte9.monitor.dashboard.domain.ServiceHealthView;
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

        switch (event.metricType()) {
            case "CONTAINER" -> projectContainer(event);
            case "SERVICE" -> projectService(event);
        }
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

    // Containers & Services

    private void projectContainer(MetricReceivedEvent event) {
        String containerName = stringValue(event.payload().get("containerName"));
        String status = stringValue(event.payload().get("status"));
        String image = stringValue(event.payload().get("image"));

        if (containerName == null) {
            return;
        }

        ContainerStatusView containerView = new ContainerStatusView(
                containerName,
                status,
                image,
                event.timestamp(),
                event.receiveAt()
        );

        repository.updateContainer(event.nodeId(), containerView);
    }

    private void projectService(MetricReceivedEvent event) {
        String serviceName = stringValue(event.payload().get("serviceName"));
        String status = stringValue(event.payload().get("status"));
        String url = stringValue(event.payload().get("url"));
        Integer httpStatus = intValue(event.payload().get("httpStatus"));

        if (serviceName == null) {
            return;
        }

        ServiceHealthView serviceView = new ServiceHealthView(
                serviceName,
                status,
                url,
                httpStatus,
                event.timestamp(),
                event.receiveAt()
        );

        repository.updateService(event.nodeId(), serviceView);
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    public List<ContainerStatusView> findContainersByNode(String nodeId) {
        return repository.findContainersByNode(nodeId);
    }

    public List<ServiceHealthView> findServicesByNode(String nodeId) {
        return repository.findServicesByNode(nodeId);
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
