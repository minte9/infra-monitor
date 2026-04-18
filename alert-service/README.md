### Alert Service

The service is responsible for evaluating incoming metrics and  
generating alerts when abnormal conditions are detected.  

    node-agent → metrics-service → RabbitMQ → alert-service
                                                ↓
                                        dashboard-service

The service listens to metric events from RabbitMQ,  
applies simple rule-based evalution, and:  

- creates alerts when thresholds are exceeded
- stores alerts in memory
- publishes alert events for downstream consumers
- exposes REST endpoints for querying alerts


### 1. Responsibilities

- Consume MetricReceivedEvent
- Evaluate alert conditions (CPU, RAM, Disk, Container, Service)
- Generate human-readable alert messages
- Assign severity (INFO, WARNING, CRITICAL)
- Store alerts (in-memory for now)
- Publish AlertTriggeredEvent
- Provide API for querying alerts

### 2. Flow

    RabbitMQ (metrics queue)
            ↓
    AlertHandlers (consumer + logic)
            ↓
    In-memory storage
            ↓
    RabbitMQ (alert events)
            ↓
    REST API (AlertController)


### 3. Project structure

    alert-service
    ├── AlertServiceApplication.java
    ├── AlertHandlers.java
    ├── config
    │   └── RabbitMqConfig.java
    ├── controller
    │   └── AlertController.java
    └── domain
        ├── AlertRecord.java
        ├── AlertResponse.java
        ├── AlertSeverity.java
        └── AlertStatus.java


### 4. Service Application

Spring Boot entry point.

- starts the application
- enables component scanning
- bootstraps RabbitMQ listeners

AlertServiceApplication.java

~~~java
@SpringBootApplication
public class AlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }
}
~~~


### 5. Alert Handlers (core logic)

This is the heart of the service.

Responsibilities:

- @RabbitListener → consumes metric events
- evaluates alert conditions
- stores alerts in memory
- publishes alert events
- exposes query methods for controller

This is implemented procedurally:

- no service layer
- no repository abstraction
- logic is organized as static/helper functions 

AlertHandlers.java

~~~java
@Component
public class AlertHandlers {
    
    private static final Logger log = LoggerFactory.getLogger(AlertHandlers.class);

    private final RabbitTemplate rabbit;
    private final List<AlertRecord> storage = new CopyOnWriteArrayList<>();

    public AlertHandlers(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    // ========================
    // RabbitMQ consumer
    // ========================

    @RabbitListener(queues = RabbitMqConfig.ALERT_QUEUE)
    public void onMetricReceived(MetricReceivedEvent event) {

        log.info("Received metric event: metricId={}, nodeId={}, metricType={}",
                    event.metricId(), 
                    event.nodeId(), 
                    event.metricType());

        Optional<AlertRecord> alert = evaluateAndStore(event, storage);

        if (alert.isPresent()) {
            log.warn("ALERT TRIGGERED: id={}, nodeId={}, metricType={}, message={}",
                alert.get().id(),
                alert.get().nodeId(),
                alert.get().metricType(),
                alert.get().message()
            );
            
            publishAlertTriggered(rabbit, alert.get());

        } else {
            log.info("No alert triggered for metricId={}", event.metricId());
        }
    }

    // ===========================
    // Procedural business logic
    // ===========================

    public Optional<AlertRecord> evaluateAndStore(
            MetricReceivedEvent event, List<AlertRecord> storage) {

        String message = buildAlertMessage(event);

        if (message == null) {
            return Optional.empty();
        }

        AlertSeverity severity = determineSeverity(event);

        AlertRecord alertRecord = new AlertRecord(
                UUID.randomUUID().toString(),
                event.metricId(),
                event.nodeId(),
                event.metricType(),
                message,
                severity,
                AlertStatus.OPEN,
                event.timestamp(),
                Instant.now(),
                event.payload()
        );

        storage.add(alertRecord);
        return Optional.of(alertRecord);
    }

    private String buildAlertMessage(MetricReceivedEvent event) {
        Map<String, Object> payload = event.payload();
        String metricType = event.metricType();
        Instant timestamp = event.timestamp();

        return switch (metricType) {
            case "CPU" -> cpuAlertMessage(event.nodeId(), payload, timestamp);
            case "RAM" -> ramAlertMessage(event.nodeId(), payload, timestamp);
            case "DISK" -> diskAlertMessage(event.nodeId(), payload, timestamp);
            case "CONTAINER" -> containerAlertMessage(event.nodeId(), payload, timestamp);
            case "SERVICE" -> serviceAlertMessage(event.nodeId(), payload, timestamp);
            default -> null;
        };
    }

    public static void publishAlertTriggered(RabbitTemplate rabbit, AlertRecord alert) {
        AlertTriggeredEvent event = new AlertTriggeredEvent(
            alert.id(),
            alert.nodeId(),
            alert.metricType(),
            alert.severity().name(),
            alert.message(),
            alert.triggeredAt(),
            alert.payload()
        );

        rabbit.convertAndSend(
            RabbitMqConfig.ALERTS_EXCHANGE,
            RabbitMqConfig.ALERT_TRIGGERED_ROUTING_KEY,
            event,
            message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }
        );
    }

    public static AlertSeverity determineSeverity(MetricReceivedEvent event) {
        String metricType = event.metricType();
        Map<String, Object> payload = event.payload();

        return switch (metricType) {
            case "CPU" -> {
                Double value = percent(payload, "usagePercent");
                yield value != null && value >= 90 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            }
            case "RAM" -> {
                Double value = percent(payload, "usagePercent");
                yield value != null && value >= 95 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            }
            case "DISK" -> {
                Double value = percent(payload, "usagePercent");
                yield value != null && value >= 95 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            }
            case "CONTAINER", "SERVICE" -> AlertSeverity.CRITICAL;
            default -> AlertSeverity.INFO;
        };
    }

    public static String cpuAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 80.0) {
            return withTime(timestamp, 
                "High CPU usage on node %s: %.2f%%".formatted(nodeId, usagePercent)
            );
        }
        return null;
    }

    public static String ramAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 85.0) {
            return withTime(timestamp, 
                "High RAM usage on node %s: %.2f%%".formatted(nodeId, usagePercent)
            );
        }
        return null;
    }

    public static String diskAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Double usagePercent = percent(payload, "usagePercent");
        if (usagePercent != null && usagePercent >= 90.0) {
            return withTime(timestamp, 
                "High disk usage on node %s: %.2f%%".formatted(nodeId, usagePercent)
            );
        }
        return null;
    }

    public static String containerAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Object rawStatus = payload.get("status");
        Object containerName = payload.getOrDefault("containerName", "unknown-container");

        if (rawStatus == null) {
            return withTime(timestamp,
                "Container %s on node %s has unknown status".formatted(containerName, nodeId)
            );
        }

        String status = rawStatus.toString().trim().toLowerCase();

        boolean isCritical =
            status.startsWith("restarting") ||
            status.startsWith("exited") ||
            status.startsWith("dead") ||
            status.contains("unhealthy");

        if (isCritical) {
            return withTime(timestamp, 
                "Container %s on node %s is %s".formatted(containerName, nodeId, rawStatus)
            );
        }

        return null;
    }

    public static String serviceAlertMessage(String nodeId, Map<String, Object> payload, Instant timestamp) {
        Object status = payload.get("status");
        Object serviceName = payload.getOrDefault("serviceName", "unknown-service");

        if (status != null && !"UP".equalsIgnoreCase(status.toString())) {
            return withTime(timestamp, 
                "Service %s on node %s is %s".formatted(serviceName, nodeId, status)
            );
        }

        return null;
    }

    public static Double percent(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    public static AlertResponse toResponse(AlertRecord alertRecord) {
        return new AlertResponse(
            alertRecord.id(),
            alertRecord.metricId(),
            alertRecord.nodeId(),
            alertRecord.metricType(),
            alertRecord.message(),
            alertRecord.severity(),
            alertRecord.status(),
            alertRecord.metricTimestamp(),
            alertRecord.triggeredAt(),
            alertRecord.payload()
        );
    }

    public static List<AlertRecord> copyStorage(List<AlertRecord> storage) {
        return new ArrayList<>(storage);
    }

    public static String withTime(Instant timestamp, String message) {
        return message + " / " + timestamp;
    }

    // ============================
    // Query methods for controller
    // ============================

    public List<AlertResponse> findAll() {
        return storage.stream()
            .map(AlertHandlers::toResponse)
            .toList();
    }

    public List<AlertResponse> findByNodeId(String nodeId) {
        return storage.stream()
            .filter(alert -> alert.nodeId().equals(nodeId))
            .map(AlertHandlers::toResponse)
            .toList();
    }

    public List<AlertResponse> findByStatus(String status) {
        return storage.stream()
            .filter(alert -> alert.status().name().equalsIgnoreCase(status))
            .map(AlertHandlers::toResponse)
            .toList();
    }
    
}
~~~


### 6. Domain

Contains core business data.   

domain/AlertRecord.java

~~~java
public record AlertRecord(
    String id,
    String metricId,
    String nodeId,
    String metricType,
    String message,
    AlertSeverity severity,
    AlertStatus status,
    Instant metricTimestamp,
    Instant triggeredAt,
    Map<String, Object> payload
) {}
~~~

domain/AlertResponse.java

~~~java
public record AlertResponse(
    String id,
    String metricId,
    String nodeId,
    String metricType,
    String message,
    AlertSeverity severity,
    AlertStatus status,
    Instant metricTimestamp,
    Instant triggeredAt,
    Map<String, Object> payload
) {}
~~~

domain/AlertSeverity.java

~~~java
public enum AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}
~~~

domain/AlertStatus.java

~~~java
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED
}
~~~


### 7. Controller

REST API layer.

controller/AlertController.java

~~~java
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertHandlers alerts;

    public AlertController(AlertHandlers alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    public List<AlertResponse> findAll() {
        return alerts.findAll();
    }

    @GetMapping("/node/{nodeId}")
    public List<AlertResponse> findByNodeId(@PathVariable String nodeId) {
        return alerts.findByNodeId(nodeId);
    }

    @GetMapping("/status/{status}")
    public List<AlertResponse> findByStatus(@PathVariable String status) {
        return alerts.findByStatus(status);
    }
}
~~~


### 8. Rabbit Config

The class RabbitMqConfig defines:

- queue
- exchanges
- routing keys

Used by:

- consumer (incoming metrics)
- publisher (alert events)

~~~java
@Configuration
public class RabbitMqConfig {

    public static final String METRICS_EXCHANGE = "metrics.exchange";
    public static final String ALERTS_EXCHANGE = "alerts.exchange";

    public static final String ALERT_QUEUE = "metrics.alert.queue";

    public static final String METRIC_RECEIVED_ROUTING_KEY = "metric.received";
    public static final String ALERT_TRIGGERED_ROUTING_KEY = "alert.triggered";

    @Bean
    public DirectExchange metricsExchange() {
        return new DirectExchange(METRICS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange alertsExchange() {
        return new DirectExchange(ALERTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue alertQueue() {
        return QueueBuilder.durable(ALERT_QUEUE).build();
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


### 9. Running the Service

Build

~~~sh
./gradlew clean
./gradlew :alert-service:build

docker compose up -d --build alert-service
~~~


### 10. Testing

Trigger alerts by:

- sending high CPU/RAM metrics from node-agent
- stopping a container/service

~~~sh
curl -X POST http://localhost:8081/api/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "nodeId": "vps-01",
    "metricType": "CPU",
    "timestamp": "2026-04-03T10:15:30Z",
    "payload": {
      "usagePercent": 82.1,
      "systemLoad": 2.31
    }
  }'

{
  "id": "69d2884443cadf4369a1457b",
  "nodeId": "vps-01",
  "metricType": "CPU",
  "timestamp": "2026-04-03T10:15:30Z",
  "payload": {
    "usagePercent": 82.1,
    "systemLoad": 2.31
  },
  "receiveAt": "2026-04-05T16:05:24.692809301Z"
}
~~~

Then check:

~~~sh
docker compose logs -f alert-service
~~~
