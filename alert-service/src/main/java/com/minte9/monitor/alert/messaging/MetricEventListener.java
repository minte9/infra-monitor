/**
 * This class listens to the RabbitMQ queue and receives
 * MetricReceiveEvent messages. 
 * 
 * Flow:
 * RabbitMQ queue -> @RabbitListener -> AlertEvaluationService
 * 
 * If an alert is triggered, we log it.
 * If no alert is needed, we simply ignore the event. 
 */

package com.minte9.monitor.alert.messaging;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.minte9.monitor.alert.config.RabbitMQConfig;
import com.minte9.monitor.alert.domain.AlertRecord;
import com.minte9.monitor.alert.service.AlertEvaluationService;
import com.minte9.monitor.common.events.MetricReceivedEvent;

@Component
public class MetricEventListener {
    
    private static final Logger log = LoggerFactory.getLogger(MetricEventListener.class);

    private final AlertEvaluationService alertEvaluationService;

    public MetricEventListener(AlertEvaluationService alertEvaluationService) {
        this.alertEvaluationService = alertEvaluationService;
    }

    @RabbitListener(queues = RabbitMQConfig.ALERT_QUEUE)
    public void onMetricReceived(MetricReceivedEvent event) {
        log.info("Received metric event: metricId={}, nodeId={}, metricType={}",
                    event.metricId(), event.nodeId(), event.metricType());

        Optional<AlertRecord> alert = alertEvaluationService.evaluate(event);

        if (alert.isPresent()) {
            log.warn("ALERT TRIGGERED: id={}, nodeId={}, metricType={}, message={}",
                alert.get().id(),
                alert.get().nodeId(),
                alert.get().metricType(),
                alert.get().message()
            );
        } else {
            log.info("No alert triggered for metricId={}", event.metricId());
        }
    }
}
