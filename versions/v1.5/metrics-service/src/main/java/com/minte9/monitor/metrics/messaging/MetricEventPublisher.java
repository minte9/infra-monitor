package com.minte9.monitor.metrics.messaging;

import com.minte9.monitor.common.events.MetricReceivedEvent;
import com.minte9.monitor.metrics.config.RabbitMqConfig;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class MetricEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MetricEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void publish(MetricReceivedEvent event) {
        rabbitTemplate.convertAndSend(
            RabbitMqConfig.METRICS_EXCHANGE,
            RabbitMqConfig.METRIC_RECEIVED_ROUTING_KEY,
            event,
            message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }
        );
    }

    // Durable queues alone do not makes messages persistent.
    // The publisher messages also need to be marked persistent.
    // RabbitMQ docs: 
    //  - durability of queues/exchanges is separate from persistence of messages.
}
