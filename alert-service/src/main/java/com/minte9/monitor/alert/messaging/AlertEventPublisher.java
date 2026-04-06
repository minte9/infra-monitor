/**
 * Publishes dashboard-consumable alert events 
 * after alert-service creates an alert.
 */

package com.minte9.monitor.alert.messaging;

import com.minte9.monitor.alert.config.RabbitMqConfig;
import com.minte9.monitor.common.events.AlertTriggeredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(AlertEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public AlertEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(AlertTriggeredEvent event) {
        log.info("Publishing AlertTriggeredEvent: alertId={} nodeId={} metricType={} severity={}",
                event.alertId(), event.nodeId(), event.metricType(), event.severity());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ALERTS_EXCHANGE,
                RabbitMqConfig.ALERT_TRIGGERED_ROUTING_KEY,
                event,
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                }
        );
    }
}