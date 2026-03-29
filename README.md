# Home Infrastructure Monitor (VPS)

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


### 1. Project structure

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


### 2. Metrics service

~~~sh
./gradlew build
./gradlew :metrics-service:bootRun
~~~
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
  
{"id":"5befcab2-c1e5-4fc7-a48a-5572d5e47075","nodeId":"vps-01","metricType":"CPU","timestamp":"2026-03-28T10:15:30Z",
 "payload":{"usagePercent":67.4,"systemLoad":1.82},"receiveAt":"2026-03-29T10:20:56.607948998Z"}
~~~

### 3. MongoDB 

~~~sh
docker compose up --build
curl http://localhost:8081/actuator/health
{"status":"UP"}
~~~

~~~sh
docker compose up -d
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


### 4. Test metric ingestion 

metrics/controller/MetricsMetricsControllerIntegrationTest.java

~~~sh
./gradlew :metrics-service:test --info

BUILD SUCCESSFUL in 1s
8 actionable tasks: 8 up-to-date
~~~
