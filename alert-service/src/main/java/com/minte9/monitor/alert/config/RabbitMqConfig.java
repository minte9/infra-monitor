/**
 * RabbitMQConfig:
 * ---------------
 * This configuration declares the queue that 
 * alert-service consumes from. 
 * 
 * Important:
 * metrics-service already publishes to:
 *  - exchange: metrics.exchange
 *  - routing key: metric.received
 *  - queue: metrics.alert.queue
 * 
 * In real systems, declarations may live in one service only, 
 * or be managed by infrastructure.
 */

package com.minte9.monitor.alert.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String METRICS_EXCHANGE = "metrics.exchange";
    public static final String ALERTS_EXCHANGE = "alerts.exchange";

    public static final String ALERT_QUEUE = "metrics.alert.queue";

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
    public Queue alertQueue() {
        return QueueBuilder.durable(ALERT_QUEUE).build();
    }

    @Bean
    public Binding metricReceivedBinding(Queue alertQueue, DirectExchange metricsExchange) {
        return BindingBuilder
                .bind(alertQueue)
                .to(metricsExchange)
                .with(METRIC_RECEIVED_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
