### Single-file procedural node-agent

~~~java
package com.minte9.monitor.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

public class Main {

    static final ObjectMapper json = new ObjectMapper();
    static final HttpClient http = HttpClient.newHttpClient();
    static final SystemInfo systemInfo = new SystemInfo();
    static long[] previousCpuTicks =
            systemInfo.getHardware().getProcessor().getSystemCpuLoadTicks();

    public static void main(String[] args) throws Exception {
        Config config = loadConfig();

        while (true) {
            try {
                List<Map<String, Object>> metrics = new ArrayList<>();

                metrics.addAll(collectSystemMetrics(config.nodeId));
                if (config.dockerEnabled) {
                    metrics.addAll(collectDockerMetrics(config.nodeId));
                }
                metrics.addAll(collectHealthMetrics(config));

                for (Map<String, Object> metric : metrics) {
                    sendMetric(config.metricsServiceBaseUrl, metric);
                }

                System.out.println("Cycle done, sent " + metrics.size() + " metrics");
            } catch (Exception e) {
                System.err.println("Cycle failed: " + e.getMessage());
                e.printStackTrace();
            }

            Thread.sleep(config.intervalMs);
        }
    }

    static Config loadConfig() {
        Config c = new Config();
        c.nodeId = env("NODE_ID", "vps-01");
        c.metricsServiceBaseUrl = env("METRICS_SERVICE_BASE_URL", "http://localhost:8081");
        c.intervalMs = Long.parseLong(env("INTERVAL_MS", "15000"));
        c.dockerEnabled = Boolean.parseBoolean(env("DOCKER_ENABLED", "true"));

        c.healthTargets = List.of(
            new HealthTarget("metrics-service", "http://localhost:8081/actuator/health"),
            new HealthTarget("alert-service", "http://localhost:8082/actuator/health"),
            new HealthTarget("dashboard-service", "http://localhost:8083/actuator/health")
        );
        return c;
    }

    static List<Map<String, Object>> collectSystemMetrics(String nodeId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Instant now = Instant.now();

        result.add(cpuMetric(nodeId, now));
        result.add(ramMetric(nodeId, now));
        result.add(diskMetric(nodeId, now));

        return result;
    }

    static Map<String, Object> cpuMetric(String nodeId, Instant timestamp) {
        CentralProcessor processor = systemInfo.getHardware().getProcessor();

        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0;
        previousCpuTicks = processor.getSystemCpuLoadTicks();

        double[] loadAverages = processor.getSystemLoadAverage(1);
        double loadAverage = loadAverages.length > 0 ? loadAverages[0] : 0.0;

        Map<String, Object> payload = new HashMap<>();
        payload.put("usagePercent", round(cpuLoad));
        payload.put("systemLoadAverage", round(loadAverage));

        return metric(nodeId, "CPU", timestamp, payload);
    }

    static Map<String, Object> ramMetric(String nodeId, Instant timestamp) {
        GlobalMemory memory = systemInfo.getHardware().getMemory();

        long totalBytes = memory.getTotal();
        long availableBytes = memory.getAvailable();
        long usedBytes = totalBytes - availableBytes;

        Map<String, Object> payload = new HashMap<>();
        payload.put("totalMb", round(bytesToMb(totalBytes)));
        payload.put("usedMb", round(bytesToMb(usedBytes)));
        payload.put("usagePercent", round(totalBytes == 0 ? 0.0 : ((double) usedBytes / totalBytes) * 100.0));

        return metric(nodeId, "RAM", timestamp, payload);
    }

    static Map<String, Object> diskMetric(String nodeId, Instant timestamp) {
        FileSystem fileSystem = systemInfo.getOperatingSystem().getFileSystem();

        long totalBytes = 0L;
        long usableBytes = 0L;

        for (OSFileStore store : fileSystem.getFileStores()) {
            totalBytes += store.getTotalSpace();
            usableBytes += store.getUsableSpace();
        }

        long usedBytes = totalBytes - usableBytes;

        Map<String, Object> payload = new HashMap<>();
        payload.put("totalGb", round(bytesToGb(totalBytes)));
        payload.put("usedGb", round(bytesToGb(usedBytes)));
        payload.put("usagePercent", round(totalBytes == 0 ? 0.0 : ((double) usedBytes / totalBytes) * 100.0));

        return metric(nodeId, "DISK", timestamp, payload);
    }

    static List<Map<String, Object>> collectDockerMetrics(String nodeId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Instant now = Instant.now();

        try {
            Process process = new ProcessBuilder(
                "docker", "ps", "-a", "--format", "{{.Names}}||{{.Image}}||{{.Status}}"
            ).start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|\\|", 3);
                    if (parts.length < 3) continue;

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("containerName", parts[0]);
                    payload.put("image", parts[1]);
                    payload.put("status", parts[2]);

                    result.add(metric(nodeId, "CONTAINER", now, payload));
                }
            }

            process.waitFor();
        } catch (Exception e) {
            System.err.println("Docker metrics failed: " + e.getMessage());
        }

        return result;
    }

    static List<Map<String, Object>> collectHealthMetrics(Config config) {
        List<Map<String, Object>> result = new ArrayList<>();
        Instant now = Instant.now();

        for (HealthTarget target : config.healthTargets) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("serviceName", target.serviceName);
            payload.put("url", target.url);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(target.url))
                        .GET()
                        .build();

                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());

                payload.put("httpStatus", response.statusCode());
                payload.put("status",
                        response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"")
                                ? "UP" : "DOWN");

            } catch (Exception e) {
                payload.put("httpStatus", -1);
                payload.put("status", "DOWN");
                payload.put("error", e.getClass().getSimpleName());
            }

            result.add(metric(config.nodeId, "SERVICE_HEALTH", now, payload));
        }

        return result;
    }

    static void sendMetric(String baseUrl, Map<String, Object> metric) throws Exception {
        String body = json.writeValueAsString(metric);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/metrics"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<Void> response =
                http.send(request, HttpResponse.BodyHandlers.discarding());

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }
    }

    static Map<String, Object> metric(
            String nodeId,
            String metricType,
            Instant timestamp,
            Map<String, Object> payload
    ) {
        Map<String, Object> m = new HashMap<>();
        m.put("nodeId", nodeId);
        m.put("metricType", metricType);
        m.put("timestamp", timestamp.toString());
        m.put("payload", payload);
        return m;
    }

    static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static double bytesToMb(long bytes) {
        return bytes / 1024.0 / 1024.0;
    }

    static double bytesToGb(long bytes) {
        return bytes / 1024.0 / 1024.0 / 1024.0;
    }

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static class Config {
        String nodeId;
        String metricsServiceBaseUrl;
        long intervalMs;
        boolean dockerEnabled;
        List<HealthTarget> healthTargets = new ArrayList<>();
    }

    static class HealthTarget {
        String serviceName;
        String url;

        HealthTarget(String serviceName, String url) {
            this.serviceName = serviceName;
            this.url = url;
        }
    }
}
~~~