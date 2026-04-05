package com.minte9.monitor.metrics.config;

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
    public static final String ALERT_QUEUE = "metrics.alert.queue";
    public static final String METRIC_RECEIVED_ROUTING_KEY = "metric.received";

    @Bean
    public Queue alertQueue() {
        return QueueBuilder.durable(ALERT_QUEUE).build();
    }

     @Bean
    public DirectExchange metricsExchange() {
        return new DirectExchange(METRICS_EXCHANGE, true, false);
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
