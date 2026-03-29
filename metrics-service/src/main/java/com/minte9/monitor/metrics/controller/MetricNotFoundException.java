package com.minte9.monitor.metrics.controller;

public class MetricNotFoundException extends RuntimeException {
    public MetricNotFoundException(String message) {
        super(message);
    }
}
