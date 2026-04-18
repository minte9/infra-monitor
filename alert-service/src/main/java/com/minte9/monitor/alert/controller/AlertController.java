/**
 * These endpoints are useful for manual testing.
 * 
 * We can verify:
 *  - which alerts where triggered
 *  - which node they belong to
 *  - wheter they are OPEN/AKNOWLEDGED/RESOLVED 
 */

package com.minte9.monitor.alert.controller;

import com.minte9.monitor.alert.AlertHandlers;
import com.minte9.monitor.alert.domain.AlertResponse;
import com.minte9.monitor.alert.domain.AlertRecord;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertHandlers alerts;

    public AlertController(AlertHandlers alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    public List<AlertResponse> findAll() {
        return alerts.findAll();
    }

    @GetMapping("/node/{nodeId}")
    public List<AlertResponse> findByNodeId(@PathVariable String nodeId) {
        return alerts.findByNodeId(nodeId);
    }

    @GetMapping("/status/{status}")
    public List<AlertResponse> findByStatus(@PathVariable String status) {
        return alerts.findByStatus(status);
    }
}