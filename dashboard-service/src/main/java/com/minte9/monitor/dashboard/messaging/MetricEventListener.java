/**
 * Consumes metric events and updates the dashboard projection. 
 */

package com.minte9.monitor.dashboard.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.minte9.monitor.common.events.MetricReceivedEvent;
import com.minte9.monitor.dashboard.config.RabbitMqConfig;
import com.minte9.monitor.dashboard.service.DashboardProjectionService;

@Component
public class MetricEventListener {
    
    private final DashboardProjectionService service;

    public MetricEventListener(DashboardProjectionService service) {
        this.service = service;
    }

    @RabbitListener(queues = RabbitMqConfig.DASHBOARD_METRICS_QUEUE)
    public void onMetricReceived(MetricReceivedEvent event) {
        service.applyMetricEvent(event);
    }
}
