/**
 * Consumes alert events and updates the dashboard projection.
 */

package com.minte9.monitor.dashboard.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.minte9.monitor.common.events.AlertTriggeredEvent;
import com.minte9.monitor.dashboard.config.RabbitMqConfig;
import com.minte9.monitor.dashboard.service.DashboardProjectionService;

@Component
public class AlertEventListener {
 
    private final DashboardProjectionService service;

    public AlertEventListener(DashboardProjectionService service) {
        this.service = service;
    }
    
    @RabbitListener(queues = RabbitMqConfig.DASHBOARD_ALERTS_QUEUE) 
    public void onAlertTriggered(AlertTriggeredEvent event) {
        service.applyAlertEvent(event);
    }
}
