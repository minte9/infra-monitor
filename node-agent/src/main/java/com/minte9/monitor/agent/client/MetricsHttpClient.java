/**
 * This class is responsible only for outbound HTTP calls.
 * 
 * Separation of responsibility matters:
 *  - collectors collect
 *  - scheduler orchestrates
 *  - client sends 
 */

package com.minte9.monitor.agent.client;

import com.minte9.monitor.agent.config.NodeAgentProperties;
import com.minte9.monitor.common.api.MetricIngestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MetricsHttpClient {

    private final RestClient restClient;
    private static final Logger log = LoggerFactory.getLogger(MetricsHttpClient.class);
    
    // Constructor
    public MetricsHttpClient(NodeAgentProperties properties) {
       this.restClient = RestClient
                .builder()
                .baseUrl(properties.getMetricsServiceBaseUrl())
                .build();
    }

    // Post metric
    public void sendMetric(MetricIngestRequest request) {
        restClient.post()
                  .uri("/api/metrics")
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(request)
                  .retrieve()
                  .toBodilessEntity();
        
        log.info("Metric send successfully: nodeId={}, metricType={}", request.nodeId(), request.metricType());
    }
}