# Home Infrastructure Monitor (VPS) - v1.4

## Test metric injestion properly

@SpringBootTest is the standard Boot annotation for integration-style tests,   
and MockMvc is useful for server-side HTTP testing without needing a live servlet container.

Before testing, let’s make the service a bit more useful.  

~~~java
// MetricMongoRepository.java

public interface MetricMongoRepository extends MongoRepository<MetricRecord, String> {
    ...
    Optional<MetricRecord> findFirstByNodeIdOrderByTimestampDesc(String nodeId);
    Optional<MetricRecord> findFirstByNodeIdAndMetricTypeOrderByTimestampDesc(String nodeId, MetricType metricType);
}
~~~
~~~java
@Service
public class MetricsIngestionService {
    ...

    public Optional<MetricRecord> findLatestByNodeId(String nodeId) {
        return metricMongoRepository.findFirstByNodeIdOrderByTimestampDesc(nodeId);
    }

    public Optional<MetricRecord> findLatestByNodeIdAndMetricType(String nodeId, MetricType metricType) {
        return metricMongoRepository.findFirstByNodeIdAndMetricTypeOrderByTimestampDesc(nodeId, metricType);
    }
}
~~~
~~~java
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    ...

    @GetMapping("/node/{nodeId}/latest")
    public MetricResponse findLatestByNodeId(@PathVariable String nodeId) {
        MetricRecord record = metricsIngestionService.findLatestByNodeId(nodeId)
                .orElseThrow(() -> new MetricNotFoundException("No metrics found for node: " + nodeId));
        return toResponse(record);
    }

    @GetMapping("/node/{nodeId}/latest/{metricType}")
    public MetricResponse findLatestByNodeIdAndMetricType(@PathVariable String nodeId,
                                                          @PathVariable MetricType metricType) {
        MetricRecord record = metricsIngestionService
                .findLatestByNodeIdAndMetricType(nodeId, metricType)
                .orElseThrow(() -> new MetricNotFoundException(
                        "No metrics found for node " + nodeId + " and type " + metricType));

        return toResponse(record);
    }
}
~~~

Add a 404 exception and global exception hadler:

  - /metrics/controller/MetricNotFoundException.java
  - /metrics/controller/GlobalExceptionHandler.java

Integration test against MongoDB.  
In src/test/java/com/minte9/monitor/  

  - /metrics/controller/MetricsMetricsControllerIntegrationTest.java


### Integration test agains MongoDB

The cleanest simple path for your project is to run tests against the same MongoDB you use in Compose.

metrics-service/src/test/resources/application-test.yml

~~~yml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/infra_monitor_test
      auto-index-creation: true
~~~
~~~sh
./gradlew :metrics-service:test --info

BUILD SUCCESSFUL in 1s
8 actionable tasks: 8 up-to-date
~~~

### Manual test flow

Start the app, then run command.

~~~sh
docker compose up --build
docker compose down
docker compose up -d
~~~
~~~sh
curl -X POST http://localhost:8081/api/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "nodeId": "vps-01",
    "metricType": "CPU",
    "timestamp": "2026-03-29T10:15:30Z",
    "payload": {
      "usagePercent": 67.4,
      "systemLoad": 1.82
    }
  }'

{"id":"69c95334da77a553f765608c","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-29T10:15:30Z",
 "payload":{"usagePercent":67.4,"systemLoad":1.82},"receiveAt":"2026-03-29T16:28:35.978900942Z"}
~~~
~~~sh
curl http://localhost:8081/api/metrics

[{"id":"69c946fa1bccb148a6e54f5c","nodeId":"vps-01","metricType":"RAM","timestamp":"2026-03-29T10:20:00Z",
    "payload":{"totalMb":2048,"usedMb":1400,"usagePercent":68.36},"receiveAt":"2026-03-29T15:36:26.199Z"},
 {"id":"69c94b1c00cc173f87607ce4","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-28T10:15:30Z",
    "payload":{"usagePercent":67.4},"receiveAt":"2026-03-29T15:54:04.731Z"},
 {"id":"69c95334da77a553f765608c","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-29T10:15:30Z",
    "payload":{"usagePercent":67.4,"systemLoad":1.82},"receiveAt":"2026-03-29T16:28:35.978Z"}]c
~~~
~~~sh
curl http://localhost:8081/api/metrics/node/vps-01/latest/CPU

{"id":"69c95334da77a553f765608c","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-29T10:15:30Z",
 "payload":{"usagePercent":67.4,"systemLoad":1.82},"receiveAt":"2026-03-29T16:28:35.978Z"}
~~~


