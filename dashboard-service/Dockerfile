FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

COPY . .
RUN chmod +x gradlew
RUN ./gradlew :dashboard-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/dashboard-service/build/libs/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]