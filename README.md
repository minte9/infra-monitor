# Infrastructure Monitor (VPS) - v1.0.3

## 1. Project structure

### 1.1 Overview

The application will monitor:

- VPS CPU
- RAM
- Disk
- Docker containers status
- Microservices health
- Trigger alerts when something goes wrong

Event-driven, Reactive, Dockerized.  

High-Level Architecture:  

~~~sh
Node Agent  →  metrics-service  →  RabbitMQ  →  alert-service
                                          ↓
                                   dashboard-service
~~~

We will build this in this order:

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


### 1.2 Create the multi-module project

Target structure:

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

- metrics-service:    Receives metrics via REST, stores them, later publishes events
- alert-service:      Consumes metric events and decides whether alerts should trigger
- dashboard-service:  Provides APIs for dashboards and later keeps projections/read models
- node-agent:         Runs on your VPS or laptop, collects host metrics, sends them to metrics-service.
- common-api:         Shared DTOs used by REST endpoints
- common-events:      Shared event contracts for RabbitMQ messages

We use Spring Boot for service modules:
    
- metrics-service
- alert-service
- dashboard-service
- node-agent

We use Plain Java library modules for:

- common-api
- common-events


### 1.3 Gradle multi-module

Create the main build files in the root:

- infra-monitor/settings.gradle.kts  
- infra-monitor/build.gradle.kts

~~~kotlin
rootProject.name = "infra-monitor"

include(
    "metrics-service",
    "alert-service",
    "dashboard-service",
    "node-agent",
    "common-api",
    "common-events"
)
~~~
~~~kotlin
plugins {
    id("java")
}

allprojects {
    group = "com.minte9.monitor"
    version = "1.0.1"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
~~~

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

Minimal app entry points

Create one Spring Boot main class per runnable service.

- metrics-service/src/main/java/com/minte9/monitor/metrics/MetricsServiceApplication.java
- alert-service/src/main/java/com/minte9/monitor/alert/AlertServiceApplication.java
- dashboard-service/src/main/java/com/minte9/monitor/dashboard/DashboardServiceApplication.java
- node-agent/src/main/java/com/minte9/monitor/agent/NodeAgentApplication.java

Example: metrics-service/.../MetricsServiceApplication.java

~~~java
package com.minte9.monitor.metrics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MetricsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetricsServiceApplication.class, args);
    }
}
~~~

Minimal shared DTOs

- common-api/src/main/java/com/minte9/monitor/common/api/MetricType.java
- common-api/src/main/java/com/minte9/monitor/common/api/MetricIngestRequest.java

~~~java
package com.minte9.monitor.common.api;

public enum MetricType {
    CPU,
    RAM,
    DISK,
    CONTAINER,
    SERVICE_HEALTH
}
~~~
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

Minimal shared event contract

common-events/src/main/java/com/minte9/monitor/common/events/MetricReceivedEvent.java

~~~java
package com.minte9.monitor.common.events;

import java.time.Instant;
import java.util.Map;

public record MetricReceivedEvent(
        String eventId,
        String nodeId,
        String metricType,
        Instant timestamp,
        Map<String, Object> payload
) {}
~~~

For early development, string-based metricType is fine across event boundaries.

### 1.4 Minimal application.yml files

- metrics-service/src/main/resources/application.yml
- alert-service/src/main/resources/application.yml
- dashboard-service/src/main/resources/application.yml
- node-agent/src/main/resources/application.yml

Example: metrics-service/.../application.yml

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

### 1.5 Test the build

~~~sh
gradle wrapper

BUILD SUCCESSFUL in 19s
1 actionable task: 1 executed
~~~
~~~sh
./gradlew build

BUILD SUCCESSFUL in 14s
24 actionable tasks: 24 up-to-date
~~~




## 2. Implement metrics-service

~~~yml 
# ----- Metric Service --------------------------------------------------------------#
~~~

Goal for this step:

- expose a REST endpoint to receive metrics
- validate input
- map request DTOs into an internal model
- store them temporarily in memory
- return a proper HTTP response
- keep the code ready for MongoDB in Step 3

The metrics-service will have:

- POST /api/metrics
- controller
- service layer
- in-memory repository
- internal domain model
- simple query endpoint for testing:
- GET /api/metrics
- GET /api/metrics/node/{nodeId}

This keeps Step 2 practical and easy to test before adding MongoDB.

### 2.1 Package structure

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









## 3. Add MongoDB to metrics-service

~~~yml
# ---- MongoDB ---------------------------------------------------------
~~~

#### 3.1 Dependency Injection (DI)

    HTTP POST /api/metrics
            ↓
    MetricsController
            ↓
    MetricsIngestionService
            ↓
    MetricRepository (interface)
            ↓
    InMemoryMetricRepository (implementation)

You NEVER create manually the repository (new InMemoryMetricRepository()),    
Spring does it for you.  

Spring sees your repository implementation exists.

~~~java
@Repository
public class InMemoryMetricRepository implements MetricRepository {
~~~

Because of @Repository, Spring:

- detects it during startup (component scanning)
- creates an instance (a “bean”)

Spring sees your service needs a repository.  
Spring resolves the dependency.   
Spring looks for MetricRepository and finds InMemoryMetricRepository.  

So Spring does internally:
~~~java
MetricRepository repo = new InMemoryMetricRepository();
MetricsIngestionService service = new MetricsIngestionService(repo);
~~~

Why you don't see it being called:

- you depend on the interface
- Spring inject the implementation
- Java polymorphism resolves at runtime

If you had multiple implementations, Spring would complain.

    InMemoryMetricRepository
    MongoMetricRepository
    -- NoUniqueBeanDefinitionException

Now, we will replace InMemoryMetricRepository with MongoMetricRepository.  
And we will NOT change controller and service, only the implementation.   


### 3.2 Add MongoDB dependency

Replace in-memory repository with a Spring Data Mongo repository  
and mapping MatricRecord to a Mongo document.

Update 

~~~kotlin
// metrics-service/build.gradle.kts

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
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")  // Look Here

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
~~~

### 3.3 Mondo document

Replace the old domain object with a Mongo document.

~~~java
// metrics/domain/MetricRecord.java

@Document(collection = "metrics")
public record MetricRecord(
    @Id
    String id,

    @Indexed
    String nodeId,
    
    MetricType metricType,
    Instant timestamp,
    Map<String, Object> payload,
    Instant receivedAt
) {}
~~~

### 3.4 Repository

Replace the custom repository interface.  
Delete these two files:  

    metrics/repository/MetricRepository.java
    metrics/repository/InMemoryMetricRepository.java

And create a Spring Data repository instead.

~~~java
// metrics/repository/MetricMongoRepository.java

public interface MetricMongoRepository extends MongoRepository<MetricRecord, String> {
    List<MetricRecord> findByNodeId(String nodeId);
    List<MetricRecord> findByNodeIdAndMetricType(String nodeId, MetricType metricType);
    List<MetricRecord> findByTimestampAfter(Instant timestamp);
}
~~~

With SpringData MongoDB, you usually write only the interface, not the implementation class.  
Spring creates the implementation behind the scenes.  

### 3.5 Service

Update the service to use Mongo.

~~~java
// metrics/service/MetricsIngestionService.java

@Service
public class MetricsIngestionService {
    private final MetricMongoRepository metricMongoRepository;
    public MetricsIngestionService(MetricMongoRepository metricMongoRepository) {
        this.metricMongoRepository = metricMongoRepository;
    }
~~~

### 3.6 MongoDB connection

Update metrics-service/src/main/resources/application.yml

~~~yml
server:
  port: 8081

spring:
  application:
    name: metrics-service
  data:
    mongodb:
      uri: mongodb://localhost:27017/infra_monitor
      auto-index-creation: true

management:
  endpoints:
    web:
      exposure:
        include: health,info
~~~

This points metrics-service to a local MongoDB database named infra_monitor.  



## 3.7 Docker Compose for metric-service + MongoDB

Create root docker-compose.yml

~~~sh
version: "3.9"

services:
  mongodb:
    image: mongo:7
    container_name: infra-monitor-mongo
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 10

  metrics-service:
    build:
      context: .
      dockerfile: metrics-service/Dockerfile
    container_name: infra-monitor-metrics-service
    depends_on:
      mongodb:
        condition: service_healthy
    ports:
      - "8081:8081"
    environment:
      SPRING_DATA_MONGODB_URI: mongodb://mongodb:27017/infra_monitor
      SPRING_DATA_MONGODB_AUTO_INDEX_CREATION: "true"
    restart: unless-stopped

volumes:
  mongo_data:
~~~


### 3.8 Dockerfile

This builds only the Boot jar for metrics-service, then runs it in a smaller JRE image.  
metrics-service/Dockerfile

~~~sh
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew :metrics-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /app/metrics-service/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
~~~

Spring Boot supports externalized configuration, including environment-variable override.  
metrics-service/src/main/resources/application.yml

~~~yml
server:
  port: 8081

spring:
  application:
    name: metrics-service
  data:
    mongodb:
      uri: ${SPRING_DATA_MONGODB_URI:mongodb://localhost:27017/infra_monitor}
      auto-index-creation: ${SPRING_DATA_MONGODB_AUTO_INDEX_CREATION:true}

management:
  endpoints:
    web:
      exposure:
        include: health,info
~~~

### 3.9 Test it

From the root:

~~~sh
docker compose up --build

curl http://localhost:8081/actuator/health
{"status":"UP"}

docker compose down
docker compose up -d
~~~

~~~sh
curl -X POST http://localhost:8081/api/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "nodeId": "vps-01",
    "metricType": "RAM",
    "timestamp": "2026-03-29T10:20:00Z",
    "payload": {
      "totalMb": 2048,
      "usedMb": 1400,
      "usagePercent": 68.36
    }
  }'

{"id":"69c946fa1bccb148a6e54f5c","nodeId":"vps-01","metricType":"RAM","timestamp":"2026-03-29T10:20:00Z",
 "payload":{"totalMb":2048,"usedMb":1400,"usagePercent":68.36},"receiveAt":"2026-03-29T15:36:26.199371123Z"}
~~~

~~~sh
docker compose down
docker compose up -d

curl http://localhost:8081/api/metrics

[{"id":"69c946fa1bccb148a6e54f5c","nodeId":"vps-01","metricType":"RAM","timestamp":"2026-03-29T10:20:00Z",
 "payload":{"totalMb":2048,"usedMb":1400,"usagePercent":68.36},"receiveAt":"2026-03-29T15:36:26.199Z"}]
~~~