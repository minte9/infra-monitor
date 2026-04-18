## Metrics Service

The service is responsible for receiving:

- infrastructure metrics from monitored nodes
- store them in MongoDB
- publish metric events to RabbitMQ for downstream processing

It is the first microservice in the monitoring flow:

    Node Agent -> Metrics Service -> RabbitMQ -> Alert Service
                                        |
                                        -> Dashboard Service


### Role

The service role is: 

- accept metric data over HTTP
- validate and persiste the data
- publish a MetricReceiveEvent
- expose query endpoints for stored metrics


### Responsibilities

The services handles:

- metric injestion from the node-agent
- metric persistence in MongoDB
- event publishing to RabbitMQ
- metric retrieval through REST endpoints

It does `not` decide alerts.  
It does `not` evaluate thresholds.  
It does `not` render UI.  

Those responsibilities belong to other services.


### Metric Types

These metric types are defined in the shared common module.

~~~java
package com.minte9.monitor.common.api;
public enum MetricType {
    CPU,
    RAM,
    DISK,
    CONTAINER,
    SERVICE
}
~~~


### Project Structure

    metrics-service
    ├── src/main/java/com/minte9/monitor/metrics
    │   ├── config
    │   │   └── RabbitMqConfig.java
    │   ├── controller
    │   │   ├── GlobalExceptionHandler.java
    │   │   ├── MetricNotFoundException.java
    │   │   └── MetricsController.java
    │   ├── domain
    │   │   ├── MetricRecord.java
    │   │   └── MetricResponse.java
    │   ├── repository
    │   │   └── MetricMongoRepository.java
    │   └── MetricsServiceApplication.java
    ├── src/main/resources
    ├── src/test
    ├── build.gradle.kts
    ├── Dockerfile
    └── README.md


### Service Application

This is the Spring Boot entry point.  
It starts the application and enables component scanning for the microservices.  

MetricsServiceApplication.java

~~~java
@SpringBootApplication
public class MetricsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetricsServiceApplication.class, args);
    }
    
}
~~~

### RabbitMQ Config

This class defines the RabbitMQ messaging configuration used by the service.

It typically contains:

- exchange definition
- queue definition
- routing key binding
- JSON message converter

This allows the service to publish `MetricReceiveEvent` messages in a structured format.  

config/RabbitMqConfig.java

~~~java
@Configuration
public class RabbitMqConfig {
    
    public static final String METRICS_EXCHANGE = "metrics.exchange";
    public static final String ALERT_QUEUE = "metrics.alert.queue";
    public static final String METRIC_RECEIVED_ROUTING_KEY = "metric.received";

    @Bean
    public Queue alertQueue() {
        return QueueBuilder.durable(ALERT_QUEUE).build();
    }

     @Bean
    public DirectExchange metricsExchange() {
        return new DirectExchange(METRICS_EXCHANGE, true, false);
    }

    @Bean
    public Binding metricReceivedBinding(Queue alertQueue, DirectExchange metricsExchange) {
        return BindingBuilder
                .bind(alertQueue)
                .to(metricsExchange)
                .with(METRIC_RECEIVED_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
~~~

### Rest Controller

This is the REST API layer.  
It exposes HTTP endpoints:  

- injesting metrics (POST)
- listing all metrics
- retrieving metrics by node
- retrieving latest metrics

The controller acts as the entry point for the `node-agent` and for `any consumer` that wants to query stored metric data.  

controller/MetricsController.java

~~~java
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    
    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);

    private final MetricMongoRepository metrics;
    private final RabbitTemplate rabbit;

    // Controller
    public MetricsController(MetricMongoRepository metrics, RabbitTemplate rabbit) {
        this.metrics = metrics;
        this.rabbit = rabbit;
    }

    // Endpoints
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricResponse ingest(@Valid @RequestBody MetricIngestRequest request) {

        log.info("Received metric request: {}", request);

        // Save record (repository)
        MetricRecord metricRecord = new MetricRecord(
            null,
            request.nodeId(),
            request.metricType(),
            request.timestamp(),
            request.payload(),
            Instant.now()
        );
        MetricRecord saved = metrics.save(metricRecord);

        // Publish event (rabbit)
        MetricReceivedEvent event = new MetricReceivedEvent(
            saved.id(),
            saved.nodeId(),
            saved.metricType().name(),
            saved.timestamp(),
            saved.receivedAt(),
            saved.payload()
        );

        rabbit.convertAndSend(
            RabbitMqConfig.METRICS_EXCHANGE,
            RabbitMqConfig.METRIC_RECEIVED_ROUTING_KEY,
            event,
            message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }
        );
        
        return this.toResponse(saved);
    }

    // Endpoint (default: /api/metrics)
    @GetMapping
    public List<MetricResponse> findAll() {
        return metrics.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/node/{nodeId}")
    public List<MetricResponse> findByNodeId(@PathVariable String nodeId) {
        return metrics.findByNodeId(nodeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/node/{nodeId}/latest")
    public MetricResponse findLatestByNodeId(@PathVariable String nodeId) {
        MetricRecord record = metrics.findFirstByNodeIdOrderByTimestampDesc(nodeId)
                .orElseThrow(() -> 
                    new MetricNotFoundException("No metrics found for node: " + nodeId));
        return toResponse(record);
    }

    @GetMapping("/node/{nodeId}/latest/{metricType}")
    public MetricResponse findLatestByNodeIdAndMetricType(
        @PathVariable String nodeId,
        @PathVariable MetricType metricType
    ) {
        MetricRecord record = metrics
                .findFirstByNodeIdAndMetricTypeOrderByTimestampDesc(nodeId, metricType)
                .orElseThrow(() -> 
                    new MetricNotFoundException(
                        "No metrics found for node " + nodeId + " and type " + metricType));
        return toResponse(record);
    }

    // Utills
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

### Exceptions

controller/MetricNotFoundException.java

A custom exception used when a request metric does not exit.  
Examples:

- no metrics found for a node
- no latest metric found for a given node

~~~java
public class MetricNotFoundException extends RuntimeException {
    public MetricNotFoundException(String message) {
        super(message);
    }
}
~~~

controller/GlobalExceptionHandler.java

This class centralizes API error handling.  
It converts exceptions into clean HTTP responses, such as:  

- 404 `Not found` for missing metrics
- 400 `Bad Request` for validating errors

~~~java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now());
        response.put("status", 400);
        response.put("error", "Validation failed");
        response.put("fieldErrors", fieldErrors);

        return response;
    }

    @ExceptionHandler(MetricNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(MetricNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now());
        response.put("status", 404);
        response.put("error", ex.getMessage());
        return response;
    }
}
~~~


### Domain Models

domain/MetricRecord.java

This is the MongoDB persistence model.  
It represents one stored metric document.  
This class is focus on storage.  

~~~java
@Document(collection = "metrics")
public record MetricRecord(
    @Id String id,
    @Indexed String nodeId,
    MetricType metricType,
    Instant timestamp,
    Map<String, Object> payload,
    Instant receivedAt
) {}
~~~

domain/MetricResponse.java

This is the response DTO returned by the REST API.  
It separates API output from the persistence model, which is useful  
if the public response changes later, without affecting database storage. 

~~~java
public record MetricResponse(
    String id,
    String nodeId,
    MetricType metricType,
    Instant timestamp,
    Map<String, Object> payload,
    Instant receiveAt
) {}
~~~

repository/MetricMongoRepository.java

This is the Spring Data MongoDB repository.  
It provides database access for metric records.  
The repository removes the need to write manual MongoDB queries for simple cases.  


### Request Flow

When a node-agent sends a metric:

1. the request reaches MetricsController
2. the payload is validated
3. a MetricRecord is created and saved in MongoDB
4. a MetricReceivedEvent is published to RabbitMQ
5. a response is returned to the caller

This makes the service both:

- a storage service
- an event producer


### Data Storage

Metrics are stored in MongoDB because they are:

- flexible in structure
- time-based
- easy to query by node and metric type

The payload field is intentionally flexible, since different metric types may carry different data.

Examples:

- CPU metric may contain usage percentage
- DISK metric may contain total/free/used space
- CONTAINER metric may contain container name and status


### Messaging

After a metric is saved, the service publishes an event to RabbitMQ.

This allows other microservices to react asynchronously without coupling them directly to the HTTP ingestion flow.

For example:

- alert-service can consume the event and decide whether to open an alert
- dashboard-service can later use the data for display or streaming updates

This keeps the architecture event-driven.


### Running the Service


Run with Gradle:

~~~sh
./gradlew :metrics-service:build

docker compose up -d
curl http://localhost:8081/api/metrics
~~~


### Testing

Integration tests verify:

- metric injestion
- MongoDB persistence
- REST endpoint behavior
- latest metric queries

RabbitMQ publishing can be mocked in tests so the service can be tested without a running broker.


### REST API

Example requests:

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

{
  "id": "5befcab2-c1e5-4fc7-a48a-5572d5e47075",
  "nodeId": "vps-01",
  "metricType": "CPU",
  "timestamp": "2026-03-28T10:15:30Z",
  "payload": {
    "usagePercent": 67.4,
    "systemLoad": 1.82
  },
  "receiveAt": "2026-03-29T10:20:56.607948998Z"
}
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

{
  "id": "b3391590-6275-4fbf-b283-117341ae5438",
  "nodeId": "vps-01",
  "metricType": "CONTAINER",
  "timestamp": "2026-03-28T10:16:20Z",
  "payload": {
    "containerName": "rabbitmq",
    "status": "UP",
    "image": "rabbitmq:3-management"
  },
  "receiveAt": "2026-03-29T10:23:42.920761590Z"
}
~~~

All metrics

~~~sh
curl http://localhost:8081/api/metrics

[
  {
    "id": "5befcab2-c1e5-4fc7-a48a-5572d5e47075",
    "nodeId": "vps-01",
    "metricType": "CPU",
    "timestamp": "2026-03-28T10:15:30Z",
    "payload": {
      "usagePercent": 67.4,
      "systemLoad": 1.82
    },
    "receiveAt": "2026-03-29T10:20:56.607948998Z"
  },
  {
    "id": "b3391590-6275-4fbf-b283-117341ae5438",
    "nodeId": "vps-01",
    "metricType": "CONTAINER",
    "timestamp": "2026-03-28T10:16:20Z",
    "payload": {
      "containerName": "rabbitmq",
      "status": "UP",
      "image": "rabbitmq:3-management"
    },
    "receiveAt": "2026-03-29T10:23:42.920761590Z"
  }
]
~~~

By node

~~~sh
curl http://localhost:8081/api/metrics/node/vps-01

[
  {
    "id": "5befcab2-c1e5-4fc7-a48a-5572d5e47075",
    "nodeId": "vps-01",
    "metricType": "CPU",
    "timestamp": "2026-03-28T10:15:30Z",
    "payload": {
      "usagePercent": 67.4,
      "systemLoad": 1.82
    },
    "receiveAt": "2026-03-29T10:20:56.607948998Z"
  },
  {
    "id": "b3391590-6275-4fbf-b283-117341ae5438",
    "nodeId": "vps-01",
    "metricType": "CONTAINER",
    "timestamp": "2026-03-28T10:16:20Z",
    "payload": {
      "containerName": "rabbitmq",
      "status": "UP",
      "image": "rabbitmq:3-management"
    },
    "receiveAt": "2026-03-29T10:23:42.920761590Z"
  }
]
~~~

Health

~~~sh
curl http://localhost:8081/actuator/health

{"status":"UP"}
~~~