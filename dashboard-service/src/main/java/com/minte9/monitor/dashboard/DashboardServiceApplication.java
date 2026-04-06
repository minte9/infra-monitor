/**
 * This service owns dashboard-friendly read models.
 * It listens to events and builds projections for fast queries.
 */

package com.minte9.monitor.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DashboardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DashboardServiceApplication.class, args);
    }
}