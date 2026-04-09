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
 * Each metric is sent independently.
 * 
 * Note:
 *  - fixedDelayString reads from Spring properties dynamically.
 */

package com.minte9.monitor.agent.service;

import com.minte9.monitor.agent.client.MetricsServiceClient;
import com.minte9.monitor.agent.collector.DockerMetricsCollector;
import com.minte9.monitor.agent.collector.ServiceHealthCollector;
import com.minte9.monitor.agent.collector.SystemMetricsCollector;
import com.minte9.monitor.agent.config.NodeAgentProperties;
import com.minte9.monitor.common.api.MetricIngestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NodeMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(NodeMonitoringScheduler.class);

    private final NodeAgentProperties properties;
    private final SystemMetricsCollector systemMetricsCollector;
    private final DockerMetricsCollector dockerMetricsCollector;
    private final ServiceHealthCollector serviceHealthCollector;
    private final MetricsServiceClient metricsServiceClient;

    public NodeMonitoringScheduler(NodeAgentProperties properties,
                                   SystemMetricsCollector systemMetricsCollector,
                                   DockerMetricsCollector dockerMetricsCollector,
                                   ServiceHealthCollector serviceHealthCollector,
                                   MetricsServiceClient metricsServiceClient) {
        this.properties = properties;
        this.systemMetricsCollector = systemMetricsCollector;
        this.dockerMetricsCollector = dockerMetricsCollector;
        this.serviceHealthCollector = serviceHealthCollector;
        this.metricsServiceClient = metricsServiceClient;
    }

    @Scheduled(fixedDelayString = "${monitor.agent.interval-ms:15000}")
    public void collectAndSendMetrics() {
        String nodeId = properties.getNodeId();
        List<MetricIngestRequest> metrics = new ArrayList<>();

        try {
            metrics.addAll(systemMetricsCollector.collect(nodeId));

            if (properties.isDockerEnabled()) {
                metrics.addAll(dockerMetricsCollector.collect(nodeId));
            }

            metrics.addAll(serviceHealthCollector.collect(nodeId));

            for (MetricIngestRequest metric : metrics) {
                try {
                    metricsServiceClient.sendMetric(metric);
                } catch (Exception e) {
                    log.error("Failed to send metric type={} nodeId={} error={}",
                            metric.metricType(), metric.nodeId(), e.getMessage());
                }
            }

            log.info("Collection cycle completed. nodeId={}, sentMetrics={}", nodeId, metrics.size());

        } catch (Exception e) {
            log.error("Node agent collection cycle failed: {}", e.getMessage(), e);
        }
    }
}