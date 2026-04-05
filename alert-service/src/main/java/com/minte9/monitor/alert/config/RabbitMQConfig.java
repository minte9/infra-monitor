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

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    public static final String ALERT_QUEUE = "metrics.alert.queue";

    @Bean
    public Queue alerQueue() {
        return QueueBuilder.durable(ALERT_QUEUE).build();
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
