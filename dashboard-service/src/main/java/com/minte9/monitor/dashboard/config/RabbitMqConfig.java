/**
 * RabbitMQ configuration for dashbord-service.
 * 
 * This service consumes:
 *  - MetricsReceiveEvent from metrics.exchange
 *  - AlertTriggeredEvent from alerts.exchange
 * 
 * Each consumer service should usually have its own queue.
 * That way, they can receive the same logical event independenty.  
 */

package com.minte9.monitor.dashboard.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
 
    public static final String METRICS_EXCHANGE = "metrics.exchange";
    public static final String ALERTS_EXCHANGE = "alerts.exchange";

    public static final String DASHBOARD_METRICS_QUEUE = "dashboard.metrics.queue";
    public static final String DASHBOARD_ALERTS_QUEUE = "dashboard.alerts.queue";

    public static final String METRIC_RECEIVED_ROUTING_KEY = "metric.received";
    public static final String ALERT_TRIGGERED_ROUTING_KEY = "alert.triggered";

    @Bean
    public DirectExchange metricsExchange() {
        return new DirectExchange(METRICS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange alertsExchange() {
        return new DirectExchange(ALERTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue dashboardMetricsQueue() {
        return QueueBuilder.durable(DASHBOARD_METRICS_QUEUE).build();
    }

    @Bean
    public Queue dashboardAlertsQueue() {
        return QueueBuilder.durable(DASHBOARD_ALERTS_QUEUE).build();
    }

    @Bean
    public Binding dashboardMetricsBinding(Queue dashboardMetricsQueue, DirectExchange metricsExchange) {
        return BindingBuilder
                .bind(dashboardMetricsQueue)
                .to(metricsExchange)
                .with(METRIC_RECEIVED_ROUTING_KEY);
    }

    @Bean
    public Binding dashboardAlertBinding(Queue dashboardAlertsQueue, DirectExchange alertsExchange) {
        return BindingBuilder
                .bind(dashboardAlertsQueue)
                .to(alertsExchange)
                .with(ALERT_TRIGGERED_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
