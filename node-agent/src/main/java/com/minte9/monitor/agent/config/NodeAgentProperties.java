/**
 * This class maps node-agent settings from application.yml
 * 
 * Instead of reading raw strings manually from the environment, 
 * Spring binds them into this type object. 
 */

package com.minte9.monitor.agent.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Validated
@ConfigurationProperties(prefix = "monitor.agent")
public class NodeAgentProperties {
    
    @NotBlank
    private String nodeId;

    @NotBlank
    private String metricsServiceBaseUrl;

    // Fixed delay in milliseconds between colletion cycles.
    private long intervalMs = 15000;

    // Whether Docker inspection is enabled.
    private boolean dockerEnabled = true;

    @Valid
    @NotEmpty
    private List<HealthTarget> healthTargets = new ArrayList<>();

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getMetricsServiceBaseUrl() {
        return metricsServiceBaseUrl;
    }

    public void setMetricsServiceBaseUrl(String metricsServiceBaseUrl) {
        this.metricsServiceBaseUrl = metricsServiceBaseUrl;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public boolean isDockerEnabled() {
        return dockerEnabled;
    }

    public void setDockerEnabled(boolean dockerEnabled) {
        this.dockerEnabled = dockerEnabled;
    }

    public List<HealthTarget> getHealthTargets() {
        return healthTargets;
    }

    public void setHealthTargets(List<HealthTarget> healthTargets) {
        this.healthTargets = healthTargets;
    }

    public static class HealthTarget {
        
        @NotBlank
        private String serviceName;

        @NotBlank
        private String url;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

}
