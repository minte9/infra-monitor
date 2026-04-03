# Home Infrastructure Monitor (VPS) - v1.5

1. Create the multi-module project 
2. Implement metrics-service 
3. Add MongoDB 
4. Test metric ingestion 
5. Add event publishing with RabbitMQ 
6. Implement alert-service 
7. Implement dashboard-service 
8. Add node-agent 
9. Docker Compose everything 
10. Prepare for VPS deployment


## 5. Add event publishing with RabbitMQ 

Spring Boot supports RabbitMQ through spring-boot-starter-amqp.    
RabbitMQ config is driven by spring.rabbitmq.* properties.  

Spring AMQP (framework) is a messaging protocol that lets different  
systems communicate by sending messages through a broker  
(instead of calling each other directly).

Spring AMQP (Advanced Message Queuing Protocol).  
A popular broker that uses AMQP is RabbitMQ.

We are only publishing in this step, alert-service will be consume in step 6.

    POST /api/metrics

    metrics-service
    ├─ save to Mongo
    └─ publish MetricReceivedEvent
              ↓
        RabbitMQ exchange
              ↓
        metrics.alert.queue


### 1) Add RabbitMQ dependency

Update metrics-service/build.gradle.kts

~~~kotlin
// Add amqp starter
implementation("org.springframework.boot:spring-boot-starter-amqp") 
~~~


### 2) Create the event contract (common-events)

common-events/.../common/events/MetricReceivedEvent.java

Keep it independent from Mongo annotations and REST DTOs. 

~~~java
public record MetricReceivedEvent(
        String metricId,
        String nodeId,
        String metricType,
        Instant timestamp,
        Instant receiveAt, 
        Map<String, Object> payload
) {}
~~~


### 3) Add RabbitMQ config (metrics-service)

Spring AMQP will work with Queue, Exchange and Binding beans for broker declaration,  
and RabbitTemplate can use JSON message converter for object payloads.  

metrics-service/.../metrics/config/RabbitMqConfig.java

~~~java
@Configuration
public class RabbitMqConfig {
    
    public static final String METRICS_EXCANGE = "metrics.exchange";
    public static final String ALERT_QUEUE = "metrics.alert.queue";
    public static final String METRIC_RECEIVED_ROUTING_KEY = "metric.received";
    
    @Bean
    public DirectExchange metricsExchange() {
        return new DirectExchange(METRICS_EXCANGE, true, false);
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


### Beans (note)

Exchange Bean  

~~~java
// A bean is simply an object that is create, managed, and stored by the Spring container.  

@Bean
public DirectExchange metricsExchange() {
    return new DirectExchange(METRICS_EXCANGE, true, false);
}

// Spring calls this method
// Creates a DirectExchange object
// Stores it as a bean named metricsExchange
~~~

Binding Beans (important)

~~~java
@Bean
public Binding metricReceivedBinding(Queue alertQueue, DirectExchange metricsExchange)

// Spring injects other beans automatically:  
//  - alertQueue
//  - metricsExchange
~~~

What Spring actually does behind the scenes:  

- Scans @Configuration classes
- Finds all @Bean methods
- Executes them
- Stores results in ApplicationContext
- Injects them wherever needed


### 4) Add an event publisher

metrics-service/.../metrics/messaging/MetricEventPublisher.java

~~~java
@Component
public class MetricEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MetricEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void publish(MetricReceivedEvent event) {
        rabbitTemplate.convertAndSend(
            RabbitMqConfig.METRICS_EXCANGE,
            RabbitMqConfig.METRIC_RECEIVED_ROUTING_KEY,
            event,
            message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }
        );
    }

    // Durable queues alone do not makes messages persistent.
    // The publisher messages also need to be marked persistent.
    // RabbitMQ docs: 
    //  - durability of queues/exchanges is separate from persistence of messages.
}
~~~


### 5) Publish after saving to MongoDB

Update your service so it saves first, then publishes.

metrics-service/.../metrics/service/MetricsIngestionService.java

~~~java
@Service
public class MetricsIngestionService {
    ...

    public MetricRecord ingest(MetricIngestRequest request) {
      MetricRecord metricRecord = new MetricRecord(
          null,
          request.nodeId(),
          request.metricType(),
          request.timestamp(),
          request.payload(),
          Instant.now()
      );

      MetricRecord saved = metricMongoRepository.save(metricRecord);

      MetricReceivedEvent event = new MetricReceivedEvent(
          saved.id(),
          saved.nodeId(),
          saved.metricType().name(),
          saved.timestamp(),
          saved.receivedAt(),
          saved.payload()
      );

       metricEventPublisher.publish(event);

      return saved;
  }
}
~~~


### 6) Add RabbitMQ properties

Update metrics-service/src/main/resources/application.yml

~~~yml
server:
  port: 8081

spring:
  ...

  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: ${SPRING_RABBITMQ_PORT:5672}
    username: ${SPRING_RABBITMQ_USERNAME:guest}
    password: ${SPRING_RABBITMQ_PASSWORD:guest}

management:
  ...
~~~


### 7) Update Docker Compose

Include RabbitMQ in docker-compose.yml

~~~yml
services:
  ...
  rabbitmq:
    image: rabbitmq:3-management
    container_name: infra-monitor-rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 10s
      timeout: 5s
      retries: 10

  metrics-service:
    depends_on:
      ...
      rabbitmq:
        condition: service_healthy

    environment:
      ...
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: "5672"
      SPRING_RABBITMQ_USERNAME: guest
      SPRING_RABBITMQ_PASSWORD: guest
~~~


### 8) Add a quick publisher test

metrics-service/src/test/resources/application-test.yml

~~~yml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/infra_monitor_test
      auto-index-creation: true
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
~~~

You can keep the existing integration test.  
It will now also exercise the publish path implicitly.  

If RabbitMQ is not running, injestion will fail because   
publishing is now part of the request flow.  


### 9) Manual test

~~~sh
docker ps
docker container prune -f

docker compose down
./gradlew clean

docker compose build --no-cache
docker compose up

docker compose logs metrics-service
./gradlew :metrics-service:compileJava --no-daemon
~~~

Send a metric:

~~~sh
curl -X POST http://localhost:8081/api/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "nodeId": "vps-01",
    "metricType": "CPU",
    "timestamp": "2026-03-29T12:00:00Z",
    "payload": {
      "usagePercent": 82.1,
      "systemLoad": 2.31
    }
  }'

{"id":"69ca3fe06ccae275d3cd9daa","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-29T12:00:00Z",
 "payload":{"usagePercent":82.1,"systemLoad":2.31},"receiveAt":"2026-03-30T09:18:24.323578743Z"}
~~~

Read it back from Mongo:

~~~sh
curl http://localhost:8081/api/metrics/node/vps-01/latest/CPU

{"id":"69ca3fe06ccae275d3cd9daa","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-29T12:00:00Z",
 "payload":{"usagePercent":82.1,"systemLoad":2.31},"receiveAt":"2026-03-30T09:18:24.323Z"}
~~~


### 10) Verify the message reached RabbitMQ

Open the RabbitMQ management UI:

    http://localhost:15672
    username: guest
    password: guest

    Your screenshot will shows:
    Queues: 1

    exchange: metrics.exchange
    queue: metrics.alert.queue
    binding key: metric.received

If no consumer exists yet, the queue’s ready message count should increase after you post metrics.

RabbitMQ routes published messages to bound queues through exchanges, so this is exactly the expected behavior before alert-service starts consuming