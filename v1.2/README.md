# Home Infrastructure Monitor (VPS) - v1.2

# Project Structure, Metric Service

Monitor:

- VPS CPU
- RAM
- Disk
- Docker containers status
- Microservices health
- Trigger alerts when something goes wrong

Event-driven, Reactive, Dockerized

### High-Level Architecture

~~~sh
Node Agent  →  metrics-service  →  RabbitMQ  →  alert-service
                                          ↓
                                   dashboard-service
~~~


### We will build this in this order:

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


## Step 1 — Create the multi-module project

Target structure

    infra-monitor/
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle/
    ├── metrics-service/
    │   ├── build.gradle.kts
    │   └── src/main/java/com/minte9/monitor/metrics/
    ├── alert-service/
    │   ├── build.gradle.kts
    │   └── src/main/java/com/minte9/monitor/alert/
    ├── dashboard-service/
    │   ├── build.gradle.kts
    │   └── src/main/java/com/minte9/monitor/dashboard/
    ├── node-agent/
    │   ├── build.gradle.kts
    │   └── src/main/java/com/minte9/monitor/agent/
    ├── common-api/
    │   ├── build.gradle.kts
    │   └── src/main/java/com/minte9/monitor/common/api/
    └── common-events/
        ├── build.gradle.kts
        └── src/main/java/com/minte9/monitor/common/events/

What each module does

- metrics-service: Receives metrics via REST, stores them, later publishes events
- alert-service: Consumes metric events and decides whether alerts should trigger
- dashboard-service: Provides APIs for dashboards and later keeps projections/read models
- node-agent: Runs on your VPS or laptop, collects host metrics, sends them to metrics-service.
- common-api: Shared DTOs used by REST endpoints
- common-events: Shared event contracts for RabbitMQ messages

Use:

Spring Boot for service modules:
    
- metrics-service
- alert-service
- dashboard-service
- node-agent

Plain Java library modules for:

- common-api
- common-events


### Gradle multi-module

Create the main build files in the root:

- settings.gradle.kts  
- build.gradle.kts

Create the other build files:

- common-api/build.gradle.kts
- common-events/build.gradle.kts
- metrics-service/build.gradle.kts
- alert-service/build.gradle.kts
- dashboard-service/build.gradle.kts
- node-agent/build.gradle.kts

Example: metrics-service/build.gradle.kts

~~~kotlin
plugins {
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    java
}

dependencies {
    implementation(project(":common-api"))
    implementation(project(":common-events"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
~~~

### Minimal app entry points

Create one Spring Boot main class per runnable service.

- metrics-service/src/main/java/com/minte9/monitor/metrics/MetricsServiceApplication.java
- alert-service/src/main/java/com/minte9/monitor/alert/AlertServiceApplication.java
- dashboard-service/src/main/java/com/minte9/monitor/dashboard/DashboardServiceApplication.java
- node-agent/src/main/java/com/minte9/monitor/agent/NodeAgentApplication.java

### Minimal shared DTOs

- common-api/src/main/java/com/minte9/monitor/common/api/MetricType.java
- common-api/src/main/java/com/minte9/monitor/common/api/MetricIngestRequest.java

### Minimal shared event contract

- common-events/src/main/java/com/minte9/monitor/common/events/MetricReceivedEvent.java

For early development, string-based metricType is fine across event boundaries.

### Minimal application.yml files

- metrics-service/src/main/resources/application.yml
- alert-service/src/main/resources/application.yml
- dashboard-service/src/main/resources/application.yml
- node-agent/src/main/resources/application.yml

### Test the build

~~~sh
gradle wrapper
./gradlew build

BUILD SUCCESSFUL in 14s
24 actionable tasks: 24 up-to-date
~~~


## Step 2. Implement metrics-service

Goal for this step:

- expose a REST endpoint to receive metrics
- validate input
- map request DTOs into an internal model
- store them temporarily in memory
- return a proper HTTP response
- keep the code ready for MongoDB in Step 3

The metrics-service will have:

    POST /api/metrics
    controller
    service layer
    in-memory repository
    internal domain model
    simple query endpoint for testing:
    GET /api/metrics
    GET /api/metrics/node/{nodeId}

This keeps Step 2 practical and easy to test before adding MongoDB.

### Package structure (TO DO)

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
        │   └── InMemoryMetricRepository.java
        ├── service/
        │   └── MetricsIngestionService.java
        └── api/
            └── MetricResponse.java

Add a health endpoint config.
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


### Example request you can send now

~~~sh
./gradlew build
./gradlew :metrics-service:bootRun
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

### Test endpoints

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

### Minimal integration test

metrics-service/src/test/java/com/example/inframonitor/metrics/controller/MetricsControllerTest.java

~~~sh
./gradlew :metrics-service:bootRun
./gradlew test

BUILD SUCCESSFUL in 8s
13 actionable tasks: 2 executed, 11 up-to-date
~~~


### What you should have after this step

At the end of Step 2, metrics-service can:

- accept metrics over HTTP
- validate requests
- store them in memory
- expose them for inspection
- serve as the base for MongoDB persistence in Step 3