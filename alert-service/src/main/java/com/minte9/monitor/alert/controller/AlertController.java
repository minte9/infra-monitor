/**
 * These endpoints are useful for manual testing.
 * 
 * We can verify:
 *  - which alerts where triggered
 *  - which node they belong to
 *  - wheter they are OPEN/AKNOWLEDGED/RESOLVED 
 */

package com.minte9.monitor.alert.controller;

import com.minte9.monitor.alert.api.AlertResponse;
import com.minte9.monitor.alert.domain.AlertRecord;
import com.minte9.monitor.alert.service.AlertEvaluationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertEvaluationService alertEvaluationService;

    public AlertController(AlertEvaluationService alertEvaluationService) {
        this.alertEvaluationService = alertEvaluationService;
    }

    @GetMapping
    public List<AlertResponse> findAll() {
        return alertEvaluationService.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/node/{nodeId}")
    public List<AlertResponse> findByNodeId(@PathVariable String nodeId) {
        return alertEvaluationService.findByNodeId(nodeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/status/{status}")
    public List<AlertResponse> findByStatus(@PathVariable String status) {
        return alertEvaluationService.findByStatus(status).stream()
                .map(this::toResponse)
                .toList();
    }

    private AlertResponse toResponse(AlertRecord alertRecord) {
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
}