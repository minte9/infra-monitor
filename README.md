# Infrastructure Monitor (VPS)

The application will monitor:

- VPS CPU
- RAM
- Disk
- Docker containers status
- Microservices health
- Trigger alerts when something goes wrong

## 1. Project structure / v1.0.1

- 1.1 Overview
- 1.2 Multi-module project
- 1.3 Gradle multi-module
- 1.4 Minimal application.yml files

## 2. Metrics service / v1.0.2

- 2.1 Package structure
- 2.2 Health endpoint config
- 2.3 Request Examples
- 2.4 Test endpoints
- 2.5 Minimal integration test

## 3. MongoDB on metrics-service / v1.0.3
- 3.1 Dependency Injection (DI)
- 3.2 MongoDB dependency
- 3.3 Mondo document
- 3.4 Repository
- 3.5 Metrics injestion service
- 3.6 MongoDB connection
- 3.7 Docker Compose for metric-service + MongoDB
- 3.8 Dockerfile
- 3.9 Test it

## 4. Test metric injestion properly * v1.0.4
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