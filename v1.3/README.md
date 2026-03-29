# Home Infrastructure Monitor (VPS) - v1.3

## Add MongoDB 


Key idea: Dependency Injection (DI)

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

    @Repository
    public class InMemoryMetricRepository implements MetricRepository {

Because of @Repository, Spring:

- detects it during startup (component scanning)
- creates an instance (a “bean”)

Spring sees your service needs a repository.  
Spring resolves the dependency.   
Spring looks for MetricRepository and finds InMemoryMetricRepository.  

So Spring does internally:

    MetricRepository repo = new InMemoryMetricRepository();
    MetricsIngestionService service = new MetricsIngestionService(repo);

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


## Add MongoDB dependency

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

### Mondo document

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

### Repository

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

### Service

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

### MongoDB connection

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



## Docker Compose for metric-service + MongoDB

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


### Dockerfile

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

### Test it

From the root:

~~~sh
docker compose up --build

curl http://localhost:8081/actuator/health
{"status":"UP"}

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
curl http://localhost:8081/api/metrics

[{"id":"69c946fa1bccb148a6e54f5c","nodeId":"vps-01","metricType":"RAM","timestamp":"2026-03-29T10:20:00Z",
 "payload":{"totalMb":2048,"usedMb":1400,"usagePercent":68.36},"receiveAt":"2026-03-29T15:36:26.199Z"}]
~~~

