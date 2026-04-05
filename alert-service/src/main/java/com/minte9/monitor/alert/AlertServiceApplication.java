/*
 * This is the main Spring Boot entry point for alert-service.
 *
 * alert-service is responsible for:
 * - consuming metric events from RabbitMQ
 * - evaluating alert conditions
 * - storing triggered alerts
 * - exposing alert data over REST
 */

package com.minte9.monitor.alert;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }
}