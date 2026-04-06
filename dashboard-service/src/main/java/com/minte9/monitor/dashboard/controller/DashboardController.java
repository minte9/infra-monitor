/**
 * Read-only dashboard endpoints
 * 
 * This controller does not injest metrics and does not trigger alerts.
 * It only exposes projection data already built from events. 
 */

package com.minte9.monitor.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minte9.monitor.dashboard.domain.AlertView;
import com.minte9.monitor.dashboard.domain.MetricView;
import com.minte9.monitor.dashboard.domain.NodeDashboardView;
import com.minte9.monitor.dashboard.service.DashboardProjectionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    
    private final DashboardProjectionService service;

    public DashboardController(DashboardProjectionService service) {
        this.service = service;
    }

    @GetMapping("/nodes")
    public List<NodeDashboardView> findAllNodes() {
        return service.findAllNodes();
    }

    @GetMapping("/nodes/{nodeId}")
    public NodeDashboardView findNode(@PathVariable String nodeId) {
        return service.findNode(nodeId);
    }

    @GetMapping("/nodes/{nodeId}/metrics")
    public Map<String, MetricView> findLatestMetricsByNode(@PathVariable String nodeId) {
        return service.findLatestMetricsByNode(nodeId);
    }

    @GetMapping("/nodes/{nodeId}/alerts")
    public List<AlertView> findAlertsByNode(@PathVariable String nodeId) {
        return service.findAlertsByNode(nodeId);
    }
}
