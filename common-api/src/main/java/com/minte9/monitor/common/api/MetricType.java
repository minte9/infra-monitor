/**
 * These metric types are shared across modules.
 * Keep this enum in common-api so node-agent and metrics-service
 * speak the same language.
 */

package com.minte9.monitor.common.api;

public enum MetricType {
    CPU,
    RAM,
    DISK,
    CONTAINER,
    SERVICE
}