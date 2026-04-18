/**
 * Consumes alert events and updates the dashboard projection.
 */

package com.minte9.monitor.dashboard.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.minte9.monitor.common.events.AlertTriggeredEvent;
import com.minte9.monitor.dashboard.config.RabbitMqConfig;
import com.minte9.monitor.dashboard.service.DashboardService;

@Component
public class AlertEventListener {
 
    private final DashboardService service;

    public AlertEventListener(DashboardService service) {
        this.service = service;
    }
    
    @RabbitListener(queues = RabbitMqConfig.DASHBOARD_ALERTS_QUEUE) 
    public void onAlertTriggered(AlertTriggeredEvent event) {
        service.applyAlertEvent(event);
    }
}
