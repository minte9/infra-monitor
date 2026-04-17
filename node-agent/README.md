## Node agent - Infra Monitor (VPS)

The node-agent runs on VPS or laptop and does four jobs:

1. collect host metrics
    - CPU
    - RAM 
    - Disc
2. inspect Docker containers
    - container name
    - image
    - runtime status
3. call health endpoints of microservices
    - /actuator/health
4. send all collected metrics to metrics-service using HTTP

The flow:

    node-agent
      -> POST /api/metrics
        -> metrics-service
          -> MongoDB
            -> RabbitMQ
              -> alert-service
    dashboard-service
      -> reads metrics + alerts for UI/API


### 1. What node-agent sends

The node-agent sends the same MetricInjestRequest already created in common-api.  

common-api/src/../common/api/MetricIngestRequest.java

~~~java
package com.minte9.monitor.common.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.Map;

public record MetricIngestRequest(
        @NotBlank String nodeId,
        @NotNull MetricType metricType,
        @NotNull Instant timestamp,
        @NotEmpty Map<String, Object> payload
) {}
~~~

Examples:

CPU metric:

~~~json
{
  "nodeId": "vps-01",
  "metricType": "CPU",
  "timestamp": "2026-04-06T10:00:00Z",
  "payload": {
    "usagePercent": 42.8,
    "systemLoadAverage": 0.91
  }
}
~~~

RAM metric

~~~json
{
  "nodeId": "vps-01",
  "metricType": "RAM",
  "timestamp": "2026-04-06T10:00:00Z",
  "payload": {
    "totalMb": 2048,
    "usedMb": 1180,
    "usagePercent": 57.61
  }
}
~~~

Disk metric

~~~json
{
  "nodeId": "vps-01",
  "metricType": "DISK",
  "timestamp": "2026-04-06T10:00:00Z",
  "payload": {
    "totalGb": 40,
    "usedGb": 22,
    "usagePercent": 55.00
  }
}
~~~

Container metric

~~~json
{
  "nodeId": "vps-01",
  "metricType": "CONTAINER",
  "timestamp": "2026-04-06T10:00:00Z",
  "payload": {
    "containerName": "infra-monitor-rabbitmq",
    "image": "rabbitmq:3-management",
    "status": "Up 5 minutes"
  }
}
~~~

Service health metric

~~~json
{
  "nodeId": "vps-01",
  "metricType": "SERVICE",
  "timestamp": "2026-04-06T10:00:00Z",
  "payload": {
    "serviceName": "metrics-service",
    "url": "http://metrics-service:8081/actuator/health",
    "status": "UP",
    "httpStatus": 200
  }
}
~~~


### 2. Shared MetricType

The shared enum should contains all types we need.

common-api/src/../common/api/MetricType.java

~~~java
/**
 * These metric types are shared across modules.
 * Keep this enum in common-api so node-agent and metrics-service
 * speak the same language.
 */

package com.minte9.monitor.common.api;

public enum MetricType {
    CPU,
    RAM,
    DISK,
    CONTAINER,
    SERVICE
}
~~~


### 3. Gradle OSHI dependency

node-agent/build.gradle.kts

OSHI gives us host metrics in pure Java, without needing shell commands for CPU, RAM, and disk.  

For Docker container status, we will use the Docker CLI for the first version,  
because it is easy to understand and works well on a VPS.  


### 4. Package structure

    node-agent/
    └── src/main/java/com/minte9/monitor/agent/
        ├── NodeAgentApplication.java
        ├── config/
        │   └── NodeAgentProperties.java
        ├── client/
        │   └── MetricsHttpClient.java
        ├── collector/
        │   ├── SystemMetricsCollector.java
        │   ├── DockerMetricsCollector.java
        │   └── ServiceHealthCollector.java
        └── service/
            └── NodeMonitoringScheduler.java


### 5. Main application class

This is the Spring Boot entry point for node-agent.  

@EnableScheduling turns on scheduled metric collection.  
@@EnableConfigurationProperties lets us bind application.yml into a typed Java properties class.

~~~java
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NodeAgentProperties.class)
public class NodeAgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NodeAgentServiceApplication.class, args);
    }
}
~~~


### 6. Configuration properties Class

This class maps node-agent settings from application.yml

node-agent/src/main/resources/application.yml

~~~yml
...

monitor:
  agent:
    node-id: vps-01
    metrics-service-base-url: http://localhost:8081
    interval-ms: 15000
    docker-enabled: true
    health-targets:
      - service-name: metrics-service
        url: http://localhost:8081/actuator/health
      - service-name: alert-service
        url: http://localhost:8082/actuator/health
      - service-name: dashboard-service
        url: http://localhost:8083/actuator/health
~~~

node-agent/src/../config/NodeAgentProperties.java

~~~java
/** 
 * Instead of reading raw strings manually from the environment, 
 * Spring binds them into this type object. 
 */

@Validated
@ConfigurationProperties(prefix = "monitor.agent")
public class NodeAgentProperties {

    ...

    @NotBlank
    private String metricsServiceBaseUrl;

    public String getMetricsServiceBaseUrl() {
        return metricsServiceBaseUrl;
    }

    public void setMetricsServiceBaseUrl(String metricsServiceBaseUrl) {
        this.metricsServiceBaseUrl = metricsServiceBaseUrl;
    }

    ...

}
~~~

Why use typed properties?

Because this is better than scattering values everywhere.   
You get:  

- validation on startup
- auto-complete in IDE
- one central place for config

Usage example:

~~~java
public MetricsServiceClient(NodeAgentProperties properties) {
    this.restClient = RestClient.builder()
            .baseUrl(properties.getMetricsServiceBaseUrl()) // Look Here
            .build();
}
~~~


### 7. HTTP client to send metrics

node-agent/src/../agent/client/MetricsServiceClient.java

~~~java
/**
 * This class is responsible only for outbound HTTP calls.
 * 
 * Separation of responsibility matters:
 *  - collectors collect
 *  - scheduler orchestrates
 *  - client sends 
 */

@Component
public class MetricsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MetricsServiceClient.class);

    private final RestClient restClient;

    public MetricsServiceClient(NodeAgentProperties properties) {
       this.restClient = RestClient.builder()
                .baseUrl(properties.getMetricsServiceBaseUrl())
                .build();
    }

    public void sendMetric(MetricIngestRequest request) {
        restClient.post()
                  .uri("/api/metrics")
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(request)
                  .retrieve()
                  .toBodilessEntity();
        
        log.info("Metric send successfully: nodeId={}, metricType={}",
                request.nodeId(), request.metricType());
    }
}
~~~

Why RestClient?

Spring Boot 3.x gives you a modern HTTP client API.


### 8. System metrics collector

node-agent/src/../agent/collector/SystemMetricsCollector.java

~~~java
/**
 * This collector uses OSHI to read host system metrics.
 * 
 * We produce MetricIngestRequest objects directly because this is the
 * format expected by metrics-service.
 */

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

@Component
public class SystemMetricsCollector {
    
    private final SystemInfo systemInfo;
    private long[] previousCpuTicks;

    public SystemMetricsCollector() {
        this.systemInfo = new SystemInfo();
        this.previousCpuTicks = systemInfo.getHardware().getProcessor().getSystemCpuLoadTicks();
    }

    public List<MetricIngestRequest> collect(String nodeId) {
        List<MetricIngestRequest> metrics = new ArrayList<>();
        Instant now = Instant.now();

        metrics.add(buildCpuMetric(nodeId, now));
        metrics.add(buildRamMetric(nodeId, now));
        metrics.add(buildDiskMetric(nodeId, now));

        return metrics;
    }

    private MetricIngestRequest buildCpuMetric(String nodeId, Instant timestamp) {
            CentralProcessor processor = systemInfo.getHardware().getProcessor();
    
            double cpuLoad = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0;
            previousCpuTicks = processor.getSystemCpuLoadTicks();
    
            double loadAverage = processor.getSystemLoadAverage(1)[0];
    
            Map<String, Object> payload = new HashMap<>();
            payload.put("usagePercent", round(cpuLoad));
            payload.put("systemLoadAverage", round(loadAverage));
    
            return new MetricIngestRequest(
                    nodeId,
                    MetricType.CPU,
                    timestamp,
                    payload
            );
        }
    
        ...
}
~~~

Important note about CPU collection.

CPU usage is not read from a single static number.  
OSHI calculates it beween two snapshots of CPU ticks.  
This is why we store `long[] previousCpuTicks`.


### 9. Docker metrics collector

The simplest practical solution is to call:

~~~sh
docker ps -a --format ...
~~~

This works well on a VPS where Docker is installed.

node-agent/src/../agent/collector/DockerMetricsCollector.java

~~~java
/**
 * This collector reads Docker container information using the Docker CLI.
 * 
 * Output format:
 *  containerName||image||status
 * 
 * Example:
 *  rabbitmq||rabbitmq:3-management||Up 10 minutes 
 */

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
~~~

Why catch exception here?

Because node-agent should keep working even if Docker inspection fails.  
For example:  
- Docker not installed
- Docker daemon unavailable
- permission issue
- container does not have Docker CLI access

We still want CPU, RAM, disk, and service health metrics to keep flowing.  


### 10. Service health collector

node-agent/src/../agent/collector/ServiceHealthCollector.java

~~~java
/**
 * This collector probes service endpoints such as:
 *  /actuator/health
 * 
 * The goal is not full deep monitoring yet. 
 * The goal is to emit a simple SERVICE metric for each known service. 
 */

@Component
public class ServiceHealthCollector {

    private static final Logger log = LoggerFactory.getLogger(ServiceHealthCollector.class);

    private final NodeAgentProperties properties;
    private final RestClient restClient;

    public ServiceHealthCollector(NodeAgentProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    public List<MetricIngestRequest> collect(String nodeId) {
        List<MetricIngestRequest> metrics = new ArrayList<>();
        Instant now = Instant.now();

        for (NodeAgentProperties.HealthTarget target : properties.getHealthTargets()) {
            metrics.add(checkTarget(nodeId, now, target));
        }

        return metrics;
    }

    private MetricIngestRequest checkTarget(String nodeId, Instant timestamp,
                                            NodeAgentProperties.HealthTarget target) {
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
~~~

Why keep service health as metrics?

Because then everything stays unified in one pipeline.  
You can:
- store health checks in MongoDB
- publish them to RabbitMQ
- trigger alerts from them
- show them in the dashboard

This is cleaner than inventing a totally separate path just for service health.


### 8.11 Scheduler that orchestrates collection

node-agent/src/../agent/service/NodeMonitoringScheduler.java

~~~java
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
~~~

Why send metrics one by one?

Because metrics-service already has a clear POST /api/metrics endpoint.


### 8.12 Node-agent application.yml

node-agent/src/main/resources/application.yml

~~~yml
server:
  port: 8084

spring:
  application:
    name: node-agent

management:
  endpoints:
    web:
      exposure:
        include: health,info

monitor:
  agent:
    node-id: vps-01
    metrics-service-base-url: http://localhost:8081
    interval-ms: 15000
    docker-enabled: true
    health-targets:
      - service-name: metrics-service
        url: http://localhost:8081/actuator/health
      - service-name: alert-service
        url: http://localhost:8082/actuator/health
      - service-name: dashboard-service
        url: http://localhost:8083/actuator/health
~~~

Out of the box, node-agent will:

- identify itself as vps-01
- send metrics to http://localhost:8081
- run every 15 seconds
- inspect Docker
- probe 3 services

Later in Docker Compose, you will change these URLs to container hostnames. 


### 8.13 Manual test flow

Start the services you already have.

~~~sh
docker compose down
./gradlew clean

docker compose build --no-cache
docker compose up -d

[+] Running 6/6
 ✔ Network infra-monitor_default              Created
 ✔ Container infra-monitor-rabbitmq           Healthy 
 ✔ Container infra-monitor-mongo              Healthy 
 ✔ Container infra-monitor-dashboard-service  Started 
 ✔ Container infra-monitor-metrics-service    Started 
 ✔ Container infra-monitor-alert-service      Started
~~~

Stop the node-agent if it already running as a daemon.

~~~sh
sudo systemctl stop node-agent
~~~

Run the agent with grale:

~~~sh
./gradlew :node-agent:build

./gradlew :node-agent:bootRun
~~~

You should see logs like:

~~~sh
Metric sent successfully: nodeId=vps-01, metricType=CPU
Metric sent successfully: nodeId=vps-01, metricType=RAM
Metric sent successfully: nodeId=vps-01, metricType=DISK
Metric sent successfully: nodeId=vps-01, metricType=CONTAINER
Metric sent successfully: nodeId=vps-01, metricType=SERVICE
Collection cycle completed. nodeId=vps-01, sentMetrics=8
~~~

Now verify through metrics-service: 

~~~sh
# Latest CPU metric
curl http://localhost:8081/api/metrics/node/vps-01/latest/CPU | jq

{
  "id": "69d79f4dd45ef46cb6ebd057",
  "nodeId": "vps-01",
  "metricType": "CPU",
  "timestamp": "2026-04-09T12:45:01.897Z",
  "payload": {
    "systemLoadAverage": 1.65,
    "usagePercent": 21.32
  },
  "receiveAt": "2026-04-09T12:45:01.981Z"
}
~~~

~~~sh
# Latest RAM metric
curl http://localhost:8081/api/metrics/node/vps-01/latest/RAM | jq

{
  "id": "69d7a2ecd45ef46cb6ebd3eb",
  "nodeId": "vps-01",
  "metricType": "RAM",
  "timestamp": "2026-04-09T13:00:28.600Z",
  "payload": {
    "totalMb": 15871.11,
    "usagePercent": 90.02,
    "usedMb": 14287.05
  },
  "receiveAt": "2026-04-09T13:00:28.676Z"
}
~~~

~~~sh
# Latest DISK metric
curl http://localhost:8081/api/metrics/node/vps-01/latest/DISK | jq

{
  "id": "69d7a30ad45ef46cb6ebd40a",
  "nodeId": "vps-01",
  "metricType": "DISK",
  "timestamp": "2026-04-09T13:00:58.869Z",
  "payload": {
    "totalGb": 926.11,
    "usedGb": 403.61,
    "usagePercent": 43.58
  },
  "receiveAt": "2026-04-09T13:00:58.975Z"
}

~~~

~~~sh
# Latest SERVICE metric
curl http://localhost:8081/api/metrics/node/vps-01/latest/SERVICE | jq

{
  "id": "69d7bb501817a1683e5d3fea",
  "nodeId": "vps-01",
  "metricType": "SERVICE",
  "timestamp": "2026-04-09T14:44:32.143Z",
  "payload": {
    "httpStatus": 200,
    "serviceName": "metrics-service",
    "url": "http://localhost:8081/actuator/health",
    "status": "UP"
  },
  "receiveAt": "2026-04-09T14:44:32.348Z"
}
~~~

~~~sh
# All metrics for the node
curl http://localhost:8081/api/metrics/node/vps-01

[
  {
    "id": "69d7a143d45ef46cb6ebd246",
    "nodeId": "vps-01",
    "metricType": "CPU",
    "timestamp": "2026-04-09T12:53:23.889Z",
    "payload": {
      "systemLoadAverage": 1.22,
      "usagePercent": 19.16
    },
    "receiveAt": "2026-04-09T12:53:23.949Z"
  },
  {
    "id": "69d7a143d45ef46cb6ebd247",
    "nodeId": "vps-01",
    "metricType": "RAM",
    "timestamp": "2026-04-09T12:53:23.889Z",
    "payload": {
      "totalMb": 15871.11,
      "usagePercent": 90.33,
      "usedMb": 14336.36
    },
    "receiveAt": "2026-04-09T12:53:23.957Z"
  },
  {
    "id": "69d7a143d45ef46cb6ebd248",
    "nodeId": "vps-01",
    "metricType": "DISK",
    "timestamp": "2026-04-09T12:53:23.889Z",
    "payload": {
      "totalGb": 926.11,
      "usedGb": 403.61,
      "usagePercent": 43.58
    },
    "receiveAt": "2026-04-09T12:53:23.970Z"
  },
  {
    "id": "69d7a143d45ef46cb6ebd249",
    "nodeId": "vps-01",
    "metricType": "CONTAINER",
    "timestamp": "2026-04-09T12:53:23.906Z",
    "payload": {
      "image": "infra-monitor-dashboard-service",
      "containerName": "infra-monitor-dashboard-service",
      "status": "Up 19 minutes"
    },
    "receiveAt": "2026-04-09T12:53:23.980Z"
  },

  ...
]
~~~


### 8.14 Cleanup strategy

`GET /api/metrics/node/vps-01`  
is returning all stored metrics for that node, so once the agent runs for a while, that gets huge. 

Clean old data from MongoDb:

~~~sh
docker exec -it infra-monitor-mongo mongosh

use infra_monitor
db.metrics.deleteMany({})
~~~

For a monitoring app, old raw grow fast.  
A good next step is a Mongo TTL index so metrics auto-expired after, say, 7 days.

~~~sh
docker exec -it infra-monitor-mongo mongosh

use infra_monitor
db.metrics.createIndex({ receivedAt: 1 }, { expireAfterSeconds: 604800 })

# check number of documents
db.metrics.countDocuments()

# 604800 = 7 days.
# This way Mongo cleans old metrics automatically
~~~


### 8.15 Separate service for node-agent

The `node-agent` is a bit special compared to the other services.  

The other services are normal backend services inside the app stack.  
But node-agent is meant to observe the `machine itself`.

Think of it like this:
- metrics-service, alert-service, dashboard-service are your application services.
- node-agent is your observer/collector

Collectors are often closer to the machine they monitor. 

For example, inside Docker it may not automatically see:
- the real host filesystem usage
- the real host Docker daemon
- the real host container list

That is why many monitoring systems run agents:  
- on the host
- as systemd services
- as privileged containers
- or as daemon-like processes


### 8.16 Run it directly (no Gradle)

~~~sh
./gradlew :node-agent:bootJar
~~~

Use systemd service (it will start automatically at restarts).

~~~sh
sudo nano /etc/systemd/system/node-agent.service
~~~
~~~sh
[Unit]
Description=Node Agent
After=network.target

[Service]
User=root
WorkingDirectory=/home/catalin/m9App/m9github/infra-monitor
ExecStart=/usr/bin/java -jar node-agent/build/libs/node-agent-1.0.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
~~~

Then:

~~~sh
sudo systemctl daemon-reexec
sudo systemctl daemon-reload
sudo systemctl enable node-agent
sudo systemctl start node-agent
~~~

Check logs:

~~~sh
journalctl -u node-agent -f
~~~

Start the microservices again with Docker.

~~~sh
docke compose up -d

curl http://localhost:8081/api/metrics/node/vps-01/latest/CPU | jq
curl http://localhost:8081/api/metrics/node/vps-01/latest/RAM | jq
curl http://localhost:8081/api/metrics/node/vps-01/latest/DISK | jq
curl http://localhost:8081/api/metrics/node/vps-01/latest/SERVICE | jq
~~~

Stop it

~~~sh
sudo systemctl start node-agent

docker compose down
~~~



### 8.17 Logs on VPS

On VPS (small disk 10-20 GB) logs can became a problem.  
If limits are high or unset, disk can fill.  

Check current usage:

~~~sh
journalctl --disk-usage

Archived and active journals take up 2.7G in the file system.
~~~

Manual cleanup (if needed):

~~~sh
sudo journalctl --vacuum-size=100M

Vacuuming done, freed 2.5G of archived journals from /var/log/journal/
~~~

For a VPS, usually set:

~~~sh
sudo nano /etc/systemd/journald.conf

SystemMaxUse=200M
SystemKeepFree=100M

sudo systemctl restart systemd-journald
~~~