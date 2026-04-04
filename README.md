# Infrastructure Monitor (VPS)

The application will monitor:

- VPS CPU
- RAM
- Disk
- Docker containers status
- Microservices health
- Trigger alerts when something goes wrong

## 1. Project structure / v1.0.1

- Overview
- Multi-module project
- Gradle multi-module
- Minimal application.yml files

## 2. Metrics service / v1.0.2

- Package structure
- Health endpoint config
- Request Examples
- Test endpoints
- Minimal integration test

## 3. MongoDB on metrics-service / v1.0.3
- Dependency Injection (DI)
- MongoDB dependency
- Mondo document
- Repository
- Metrics injestion service
- MongoDB connection
- Docker Compose for metric-service + MongoDB
- Dockerfile
- Test it

## 4. Test metric injestion properly / v1.0.4
- 4.1 Make the service a bit more useful.  
- 4.2 Exception handler
- 4.3 Integration test agains MongoDB
- 4.4 Manual test flow


## 5. Event publishing with RabbitMQ / v1.0.5
- 5.1 RabbitMQ dependency
- 5.2 Event contract (common-events) + Beans (note)
- 5.4 Event publisher
- 5.5 Publish after saving to MongoDB
- 5.6 RabbitMQ properties
- 5.7 Update Docker Compose
- 5.8 Quick publisher test
- 5.9 Manual test
- 5.10 Verify the message reached RabbitMQ