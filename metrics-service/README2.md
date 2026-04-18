## Metric Service - Infra Monitor (VPS)

The metrics-service has:

- POST /api/metrics
- GET /api/metrics
- GET /api/metrics/node/{nodeId}

### 1. Package structure

Inside metrics-service, create:

    metrics-service/
    └── src/main/java/com/minte9/monitor/metrics/
        ├── MetricsServiceApplication.java
        ├── controller/
        │   └── MetricsController.java
        ├── domain/
        │   └── MetricRecord.java
        ├── repository/
        │   ├── MetricRepository.java
        ├── service/
        │   └── MetricsIngestionService.java
        └── api/
            └── MetricResponse.java

MetricsController.java

~~~java
package com.minte9.monitor.metrics.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.metrics.api.MetricResponse;
import com.minte9.monitor.metrics.domain.MetricRecord;
import com.minte9.monitor.metrics.service.MetricsIngestionService;

import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    
    private final MetricsIngestionService metricsIngestionService;

    public MetricsController(MetricsIngestionService metricsIngestionService) {
        this.metricsIngestionService = metricsIngestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricResponse ingest(@Valid @RequestBody MetricIngestRequest request) {
        MetricRecord saved = metricsIngestionService.ingest(request);
        return toResponse(saved);
    }

    @GetMapping
    public List<MetricResponse> findAll() {
        return metricsIngestionService.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/node/{nodeId}")
    public List<MetricResponse> findByNodeId(@PathVariable String nodeId) {
        return metricsIngestionService.findByNodeId(nodeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private MetricResponse toResponse(MetricRecord record) {
        return new MetricResponse(
            record.id(),
            record.nodeId(),
            record.metricType(),
            record.timestamp(),
            record.payload(),
            record.receivedAt()
        );
    }
}
~~~

MetricsInjectionService.java

~~~java
package com.minte9.monitor.metrics.service;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import org.springframework.stereotype.Service;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.metrics.domain.MetricRecord;
import com.minte9.monitor.metrics.repository.MetricRepository;

@Service
public class MetricsIngestionService {
    
    private final MetricRepository metricRepository;

    public MetricsIngestionService(MetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    public MetricRecord ingest(MetricIngestRequest request) {
        MetricRecord metricRecord = new MetricRecord(
            UUID.randomUUID().toString(),
            request.nodeId(),
            request.metricType(),
            request.timestamp(),
            request.payload(),
            Instant.now()
        );

        return metricRepository.save(metricRecord);
    }

    public List<MetricRecord> findAll() {
        return metricRepository.findAll();
    }

    public List<MetricRecord> findByNodeId(String nodeId) {
        return metricRepository.findByNodeId(nodeId);
    }
}
~~~

### 2.2 Add a health endpoint config

This will have: GET /acuator/health  

~~~yml
server:
  port: 8081

spring:
  application:
    name: metrics-service

management:
  endpoints:
    web:
      exposure:
        include: health,info
~~~


### 2.3 Request Examples

~~~sh
./gradlew :metrics-service:build
./gradlew :metrics-service:bootRun

docker compose up -d
curl http://localhost:8081/api/metrics
~~~

CPU metric 

~~~sh
curl -X POST http://localhost:8081/api/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "nodeId": "vps-01",
    "metricType": "CPU",
    "timestamp": "2026-03-28T10:15:30Z",
    "payload": {
      "usagePercent": 67.4,
      "systemLoad": 1.82
    }
  }'
  
{"id":"5befcab2-c1e5-4fc7-a48a-5572d5e47075","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-28T10:15:30Z","payload":{"usagePercent":67.4,"systemLoad":1.82},"receiveAt":"2026-03-29T10:20:56.607948998Z"}
~~~

Container metric

~~~sh
curl -X POST http://localhost:8081/api/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "nodeId": "vps-01",
    "metricType": "CONTAINER",
    "timestamp": "2026-03-28T10:16:20Z",
    "payload": {
      "containerName": "rabbitmq",
      "status": "UP",
      "image": "rabbitmq:3-management"
    }
  }'

{"id":"b3391590-6275-4fbf-b283-117341ae5438","nodeId":"vps-01","metricType":"CONTAINER","timestamp":"2026-03-28T10:16:20Z",
  "payload":{"containerName":"rabbitmq","status":"UP","image":"rabbitmq:3-management"},"receiveAt":"2026-03-29T10:23:42.920761590Z"}
~~~

### 2.4 Test endpoints

All metrics

~~~sh
curl http://localhost:8081/api/metrics

[{"id":"5befcab2-c1e5-4fc7-a48a-5572d5e47075","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-28T10:15:30Z",
    "payload":{"usagePercent":67.4,"systemLoad":1.82},"receiveAt":"2026-03-29T10:20:56.607948998Z"},
 {"id":"b3391590-6275-4fbf-b283-117341ae5438","nodeId":"vps-01","metricType":"CONTAINER","timestamp":"2026-03-28T10:16:20Z",
    "payload":{"containerName":"rabbitmq","status":"UP","image":"rabbitmq:3-management"},"receiveAt":"2026-03-29T10:23:42.920761590Z"}]
~~~

By node

~~~sh
curl http://localhost:8081/api/metrics/node/vps-01

[{"id":"5befcab2-c1e5-4fc7-a48a-5572d5e47075","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-28T10:15:30Z",
    "payload":{"usagePercent":67.4,"systemLoad":1.82},"receiveAt":"2026-03-29T10:20:56.607948998Z"},
 {"id":"b3391590-6275-4fbf-b283-117341ae5438","nodeId":"vps-01","metricType":"CONTAINER","timestamp":"2026-03-28T10:16:20Z",
    "payload":{"containerName":"rabbitmq","status":"UP","image":"rabbitmq:3-management"},"receiveAt":"2026-03-29T10:23:42.920761590Z"}]
~~~

Health

~~~sh
curl http://localhost:8081/actuator/health

{"status":"UP"}
~~~

### 2.5 Minimal integration test

metrics-service/src/test/.../MetricsControllerTest.java

~~~java
package com.minte9.monitor.metrics.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldIngestMetric() throws Exception {
        String body = """
            {
                "nodeId": "vps-01",
                "metricType": "CPU",
                "timestamp": "2026-03-28T10:15:30Z",
                "payload": {
                "usagePercent": 67.4
                }
            }
            """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeId").value("vps-01"))
                .andExpect(jsonPath("$.metricType").value("CPU"))
                .andExpect(jsonPath("$.payload.usagePercent").value(67.4));        
    }
}
~~~

~~~sh
./gradlew test

BUILD SUCCESSFUL in 2s
13 actionable tasks: 13 up-to-date
~~~