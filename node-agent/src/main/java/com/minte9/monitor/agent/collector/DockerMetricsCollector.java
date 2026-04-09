/**
 * This collector reads Docker container information using the Docker CLI.
 * 
 * Output format:
 *  containerName||image||status
 * 
 * Example:
 *  rabbitmq||rabbitmq:3-management||Up 10 minutes 
 */

package com.minte9.monitor.agent.collector;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.common.api.MetricType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DockerMetricsCollector {
    
    private static final Logger log = LoggerFactory.getLogger(DockerMetricsCollector.class);

    public List<MetricIngestRequest> collect(String nodeId) {
        List<MetricIngestRequest> result = new ArrayList<>();
        Instant now = Instant.now();

        ProcessBuilder processBuilder = new ProcessBuilder(
            "docker", "ps", "-a",
            "--format", "{{.Names}}||{{.Image}}||{{.Status}}"
        );

        try {
            Process process = processBuilder.start();

            try(BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                String line;
                while((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|\\|", 3);

                    if (parts.length < 3) {
                        continue;
                    }

                    String containerName = parts[0];
                    String image = parts[1];
                    String status = parts[2];

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("containerName", containerName);
                    payload.put("image", image);
                    payload.put("status", status);

                    result.add(new MetricIngestRequest(
                        nodeId,
                        MetricType.CONTAINER,
                        now,
                        payload
                    ));   
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.warn("docker ps exited with non-zero code: {}", exitCode);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to collect Docker metrics: {}", e.getMessage());
        }

        return result;
    }
}
