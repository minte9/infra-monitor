/**
 * This scheduler is the heart of node-agent.
 * 
 * On each cycle it:
 *  - collects system metrics
 *  - collects Docker metrics
 *  - collects servide health metrics
 *  - sends all of them to metrics-service
 *  
 * Design:
 *  - Each metric is sent independently.
 * 
 * Note:
 *  - fixedDelayString reads from Spring properties dynamically.
 * 
 * Lombok removes constructor boilerplate via @RequiredArgsConstructor.
 */

package com.minte9.monitor.agent.service;

import com.minte9.monitor.agent.client.MetricsHttpClient;
import com.minte9.monitor.agent.collector.DockerMetricsCollector;
import com.minte9.monitor.agent.collector.ServiceHealthCollector;
import com.minte9.monitor.agent.collector.SystemMetricsCollector;
import com.minte9.monitor.agent.config.NodeAgentProperties;
import com.minte9.monitor.common.api.MetricIngestRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(NodeMonitoringScheduler.class);

    private final NodeAgentProperties properties;
    private final SystemMetricsCollector system;
    private final DockerMetricsCollector docker;
    private final ServiceHealthCollector service;
    private final MetricsHttpClient httpClient;

    @Scheduled(fixedDelayString = "${monitor.agent.interval-ms:15000}")
    public void collectAndSendMetrics() {
        
        List<MetricIngestRequest> metrics = new ArrayList<>();

        try {
            String nodeId = properties.getNodeId();
            
            // System collector
            metrics.addAll(
                system.collect(nodeId)
            );

            // Docker collector
            if (properties.isDockerEnabled()) {
                metrics.addAll(
                    docker.collect(nodeId)
                );
            }

            // Service collector
            metrics.addAll(
                service.collect(nodeId)
            );

            // Metrics client
            for (MetricIngestRequest metric : metrics) {
                try {
                    httpClient.sendMetric(metric);

                } catch (Exception e) {
                    log.error("Failed to send metric type={} nodeId={} error={}", metric.metricType(), metric.nodeId(), e.getMessage());
                }
            }
            
            log.info("Collection cycle completed. nodeId={}, sentMetrics={}", nodeId, metrics.size());

        } catch (Exception e) {
            log.error("Node agent collection cycle failed: {}", e.getMessage(), e);
        }
    }
}