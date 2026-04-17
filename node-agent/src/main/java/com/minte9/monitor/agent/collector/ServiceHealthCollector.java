/**
 * This collector probes service endpoints such as:
 *  /actuator/health
 * 
 * The goal is not full deep monitoring yet. 
 * The goal is to emit a simple SERVICE metric for each known service. 
 */

package com.minte9.monitor.agent.collector;

import com.minte9.monitor.agent.config.NodeAgentProperties;
import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.common.api.MetricType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ServiceHealthCollector {
    
    private final RestClient restClient;
    private final NodeAgentProperties properties;
    private static final Logger log = LoggerFactory.getLogger(ServiceHealthCollector.class);

    // Constructor
    public ServiceHealthCollector(NodeAgentProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    // Metrics collection (service)
    public List<MetricIngestRequest> collect(String nodeId) {
        List<MetricIngestRequest> metrics = new ArrayList<>();
        Instant now = Instant.now();

        // Check every target (UP)
        for (NodeAgentProperties.HealthTarget target : properties.getHealthTargets()) {
            metrics.add(checkTarget(nodeId, now, target));
        }

        return metrics;
    }

    // Check target (UP)
    private MetricIngestRequest checkTarget(
            String nodeId, Instant timestamp, NodeAgentProperties.HealthTarget target) {
                                                
        Map<String, Object> payload = new HashMap<>();
        payload.put("serviceName", target.getServiceName());
        payload.put("url", target.getUrl());

        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(target.getUrl())
                    .retrieve()
                    .toEntity(String.class);

            payload.put("httpStatus", response.getStatusCode().value());

            if (response.getStatusCode().is2xxSuccessful()) {
                String body = response.getBody() == null ? "" : response.getBody();

                if (body.contains("\"status\":\"UP\"")) {
                    payload.put("status", "UP");
                } else {
                    payload.put("status", "DOWN");
                }
            } else {
                payload.put("status", "DOWN");
            }

        } catch (Exception e) {
            payload.put("status", "DOWN");
            payload.put("httpStatus", -1);
            payload.put("error", e.getClass().getSimpleName());
            log.warn("Health check failed for {}: {}", target.getServiceName(), e.getMessage());
        }

        return new MetricIngestRequest(
                nodeId,
                MetricType.SERVICE,
                timestamp,
                payload
        );
    }
}
