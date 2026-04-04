# Home Infrastructure Monitor (VPS) - v1.0.1

## 1. Project structure

### 1.1 Overview

The application will monitor:

- VPS CPU
- RAM
- Disk
- Docker containers status
- Microservices health
- Trigger alerts when something goes wrong

The application will be event-driven, reactive, dockerized.  

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

#### Create the main build files in the root:

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

#### Create the other build files:

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

#### Minimal app entry points

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

#### Minimal shared DTOs

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

#### Minimal shared event contract

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

#### 1.4 Minimal application.yml files

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

### Test the build

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